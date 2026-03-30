package com.kitepromiss.obr.semantic;

import com.kitepromiss.obr.ObrException;
import com.kitepromiss.obr.ast.Attribute;
import com.kitepromiss.obr.ast.CallExpr;
import com.kitepromiss.obr.ast.Expr;
import com.kitepromiss.obr.ast.MrItem;
import com.kitepromiss.obr.ast.ObrFile;
import com.kitepromiss.obr.ast.ObrItem;
import com.kitepromiss.obr.ast.ParamDecl;
import com.kitepromiss.obr.ast.Stmt;
import com.kitepromiss.obr.ast.VarDeclarator;
import com.kitepromiss.obr.ast.VarVisibility;
import com.kitepromiss.obr.module.LoadedMrModule;
import com.kitepromiss.obr.module.ModuleBundle;
import com.kitepromiss.obr.lex.CharLiteralParser;
import com.kitepromiss.obr.module.ObrProgramBundle;
import com.kitepromiss.obr.trace.InterpreterAuditLog;
import com.kitepromiss.obr.trace.TraceCategory;
import com.kitepromiss.obr.trace.TraceLevel;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 语义绑定（P4）：先声明后定义、返回类型一致、@Overwrite/@Callfun、调用静态实参类型检查。
 */
public final class SemanticBinder {
    private static final String E_SEM_DECL_NOT_FOUND = "E_SEM_DECL_NOT_FOUND";
    private static final String E_SEM_RETURN_MISMATCH = "E_SEM_RETURN_MISMATCH";
    private static final String E_SEM_OVERWRITE_DENIED = "E_SEM_OVERWRITE_DENIED";
    private static final String E_SEM_CALL_SIG_MISMATCH = "E_SEM_CALL_SIG_MISMATCH";
    private static final String E_SEM_CALLFUN_DENIED = "E_SEM_CALLFUN_DENIED";
    private static final String E_SEM_OVERLOAD_AMBIGUOUS = "E_SEM_OVERLOAD_AMBIGUOUS";
    private static final String E_SEM_TYPE_INFER_LITERAL = "E_SEM_TYPE_INFER_LITERAL";
    private static final String E_SEM_TYPE_INFER_NAME = "E_SEM_TYPE_INFER_NAME";
    private static final String E_SEM_TYPE_INFER_EXPR = "E_SEM_TYPE_INFER_EXPR";
    private static final String E_SEM_DUP_DEF = "E_SEM_DUP_DEF";
    private static final String E_SEM_VOID_EXPR = "E_SEM_VOID_EXPR";
    private static final String E_SEM_RETURN_IN_VOID = "E_SEM_RETURN_IN_VOID";
    private static final String E_SEM_RETURN_VALUE_MISMATCH = "E_SEM_RETURN_VALUE_MISMATCH";
    private static final String E_SEM_VAR_DUP = "E_SEM_VAR_DUP";
    private static final String E_SEM_ASSIGN_TYPE = "E_SEM_ASSIGN_TYPE";
    private static final String E_SEM_STATIC_MARK_BAD = "E_SEM_STATIC_MARK_BAD";
    private static final String E_SEM_STATIC_FILE_DUP = "E_SEM_STATIC_FILE_DUP";
    private static final String E_SEM_LINK_ACCESS_DENIED = "E_SEM_LINK_ACCESS_DENIED";
    private static final String E_SEM_BREAK_OUTSIDE_LOOP = "E_SEM_BREAK_OUTSIDE_LOOP";
    private static final String E_SEM_CONTINUE_OUTSIDE_LOOP = "E_SEM_CONTINUE_OUTSIDE_LOOP";

    private SemanticBinder() {}

    private static final class VarInfo {
        final String typeKeyword;
        boolean isStatic;
        final boolean isParam;
        final VarVisibility visibility;

        VarInfo(String typeKeyword, boolean isStatic, boolean isParam, VarVisibility visibility) {
            this.typeKeyword = typeKeyword;
            this.isStatic = isStatic;
            this.isParam = isParam;
            this.visibility = visibility;
        }
    }

    /** 块作用域栈：顶为当前块；{@link #declare} 仅检查当前块是否重复。 */
    private static final class ScopeStack {
        private final List<Map<String, VarInfo>> frames = new ArrayList<>();

        void push() {
            frames.add(new HashMap<>());
        }

        void pop() {
            frames.removeLast();
        }

        void declare(Path path, String name, VarInfo info) {
            Map<String, VarInfo> top = frames.getLast();
            if (top.containsKey(name)) {
                throw new ObrException(E_SEM_VAR_DUP + " " + path + ": 同名变量重复声明: " + name);
            }
            top.put(name, info);
        }

        VarInfo resolve(String name) {
            for (int i = frames.size() - 1; i >= 0; i--) {
                VarInfo v = frames.get(i).get(name);
                if (v != null) {
                    return v;
                }
            }
            return null;
        }
    }

    /**
     * 项目内全部 {@code deRfun} 定义按「签名判定键」必须唯一（跨文件、同文件均不可重复）。
     *
     * 见仓库内 {@code docs/obr/moduleR.md} 中「先声明、后定义」与签名唯一性相关章节。
     */
    public static void assertUniqueDeRfunDefinitions(ObrProgramBundle program) {
        LinkedHashMap<FunctionSignature, Path> first = new LinkedHashMap<>();
        for (ObrProgramBundle.ParsedObrFile f : program.files()) {
            for (ObrItem item : f.ast().items()) {
                if (item instanceof ObrItem.DeRfunDef def) {
                    FunctionSignature sig = FunctionSignature.of(def.name(), def.params());
                    Path prev = first.putIfAbsent(sig, f.path());
                    if (prev != null) {
                        throw new ObrException(
                                E_SEM_DUP_DEF
                                        + " 重复的 deRfun 定义: "
                                        + sig
                                        + " 首次出现在 "
                                        + prev
                                        + "，再次出现在 "
                                        + f.path());
                    }
                }
            }
        }
    }

    public static void bindMainObr(Path projectRoot, Path mainPath, ObrFile obr, ModuleBundle modules) {
        bindMainObr(projectRoot, mainPath, obr, modules, InterpreterAuditLog.silent(), "n/a", null);
    }

    public static void bindMainObr(
            Path projectRoot, Path mainPath, ObrFile obr, ModuleBundle modules, InterpreterAuditLog audit) {
        bindMainObr(projectRoot, mainPath, obr, modules, audit, "n/a", null);
    }

    public static void bindMainObr(
            Path projectRoot,
            Path mainPath,
            ObrFile obr,
            ModuleBundle modules,
            InterpreterAuditLog audit,
            String runId,
            ProgramLinkIndex linkIndex) {
        bindObrFile(projectRoot, mainPath, obr, modules, audit, runId, linkIndex);
    }

    /** 对单个 {@code .obr} 做语义绑定（不限于 {@code main.obr}）。 */
    public static void bindObrFile(
            Path projectRoot,
            Path obrPath,
            ObrFile obr,
            ModuleBundle modules,
            InterpreterAuditLog audit,
            String runId,
            ProgramLinkIndex linkIndex) {
        bindObrFile(projectRoot, obrPath, obr, modules, audit, runId, linkIndex, null);
    }

    /**
     * 与 {@link #bindObrFile(Path, Path, ObrFile, ModuleBundle, InterpreterAuditLog, String, ProgramLinkIndex)} 相同，但可传入
     * {@link #mergeProgramFileStatics(ObrProgramBundle)} 的结果，使 {@code public static} 跨同程序内其它 {@code .obr} 可见。
     */
    public static void bindObrFile(
            Path projectRoot,
            Path obrPath,
            ObrFile obr,
            ModuleBundle modules,
            InterpreterAuditLog audit,
            String runId,
            ProgramLinkIndex linkIndex,
            Map<String, List<FileStaticRegistry.Slot>> mergedFileStatics) {
        Map<String, List<FileStaticRegistry.Slot>> fileStatics =
                mergedFileStatics != null
                        ? mergedFileStatics
                        : FileStaticRegistry.collect(obr, obrPath);
        bindSingleObr(projectRoot, obrPath, obr, collectDeclarations(modules), audit, runId, fileStatics, linkIndex);
    }

    /** 合并程序内全部 {@code .obr} 的 static 登记（{@code #LINK} 同根下的 public static 跨文件解析）。 */
    public static Map<String, List<FileStaticRegistry.Slot>> mergeProgramFileStatics(ObrProgramBundle program) {
        Map<String, List<FileStaticRegistry.Slot>> merged = new HashMap<>();
        for (ObrProgramBundle.ParsedObrFile f : program.files()) {
            Map<String, List<FileStaticRegistry.Slot>> part = FileStaticRegistry.collect(f.ast(), f.path());
            for (Map.Entry<String, List<FileStaticRegistry.Slot>> e : part.entrySet()) {
                merged.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).addAll(e.getValue());
            }
        }
        return merged;
    }

    public static void bindProgram(Path projectRoot, ObrProgramBundle program, ModuleBundle modules) {
        bindProgram(projectRoot, program, modules, InterpreterAuditLog.silent(), "n/a");
    }

    public static void bindProgram(
            Path projectRoot, ObrProgramBundle program, ModuleBundle modules, InterpreterAuditLog audit) {
        bindProgram(projectRoot, program, modules, audit, "n/a");
    }

    public static void bindProgram(
            Path projectRoot,
            ObrProgramBundle program,
            ModuleBundle modules,
            InterpreterAuditLog audit,
            String runId) {
        bindProgram(projectRoot, program, modules, audit, runId, null);
    }

    public static void bindProgram(
            Path projectRoot,
            ObrProgramBundle program,
            ModuleBundle modules,
            InterpreterAuditLog audit,
            String runId,
            ProgramLinkIndex linkIndex) {
        Map<FunctionSignature, List<DeclaredFunction>> decls = collectDeclarations(modules);
        Map<String, List<FileStaticRegistry.Slot>> programStatics = mergeProgramFileStatics(program);
        for (ObrProgramBundle.ParsedObrFile file : program.files()) {
            bindSingleObr(projectRoot, file.path(), file.ast(), decls, audit, runId, programStatics, linkIndex);
        }
    }

    private static void bindSingleObr(
            Path projectRoot,
            Path obrPath,
            ObrFile obr,
            Map<FunctionSignature, List<DeclaredFunction>> decls,
            InterpreterAuditLog audit,
            String runId,
            Map<String, List<FileStaticRegistry.Slot>> fileStatics,
            ProgramLinkIndex linkIndex) {
        String callerRel = normalizedRelPath(projectRoot, obrPath);
        for (ObrItem item : obr.items()) {
            if (!(item instanceof ObrItem.DeRfunDef def)) {
                continue;
            }
            FunctionSignature sig = FunctionSignature.of(def.name(), def.params());
            List<DeclaredFunction> matches = decls.get(sig);
            if (matches == null || matches.isEmpty()) {
                throw new ObrException(E_SEM_DECL_NOT_FOUND + " " + obrPath + ": 先声明后定义失败，未找到函数头声明: " + sig);
            }
            if (matches.size() > 1) {
                throw duplicateDeclError(sig, matches);
            }
            DeclaredFunction only = matches.getFirst();
            audit.event(
                    TraceLevel.NORMAL,
                    TraceCategory.SEMANTIC,
                    "bind_def_resolve",
                    InterpreterAuditLog.fields(
                            "run_id", runId,
                            "obr", obrPath.toString(),
                            "signature", sig.toString(),
                            "decl_module", only.moduleName() + ".mr"));
            String declaredRet = only.decl().returnType().keywordLexeme();
            String definedRet = def.returnType().keywordLexeme();
            if (!declaredRet.equals(definedRet)) {
                throw new ObrException(
                        E_SEM_RETURN_MISMATCH + " "
                                + obrPath
                                + ": 返回类型不一致: "
                                + sig
                                + "，声明="
                                + declaredRet
                                + "，定义="
                                + definedRet
                                + "（模块 "
                                + only.moduleName()
                                + ".mr）");
            }
            // moduleR 7.3：不写 @Overwrite 时等价 @Overwrite()，允许任意 .obr 实现
            AccessRule overwrite = accessRule(only.decl().attributes(), "Overwrite", AccessRule.allowAll());
            if (!overwrite.allows(callerRel)) {
                throw new ObrException(
                        E_SEM_OVERWRITE_DENIED + " "
                                + obrPath
                                + ": 实现不被 @Overwrite 允许: "
                                + sig
                                + "，caller="
                                + callerRel
                                + "，声明模块="
                                + only.moduleName()
                                + ".mr");
            }
            checkCallsInBody(obrPath, callerRel, def, decls, audit, runId, fileStatics, sig, linkIndex);
        }
    }

    private static Map<FunctionSignature, List<DeclaredFunction>> collectDeclarations(ModuleBundle modules) {
        Map<FunctionSignature, List<DeclaredFunction>> out = new LinkedHashMap<>();
        for (LoadedMrModule lm : modules.loadOrder()) {
            collectMrItems(lm.moduleName(), lm.ast().items(), out);
        }
        return out;
    }

    private static void collectMrItems(
            String moduleName, List<MrItem> items, Map<FunctionSignature, List<DeclaredFunction>> out) {
        for (MrItem item : items) {
            if (item instanceof MrItem.DeRfunDecl d) {
                FunctionSignature sig = FunctionSignature.of(d.name(), d.params());
                out.computeIfAbsent(sig, k -> new ArrayList<>()).add(new DeclaredFunction(moduleName, d));
            } else if (item instanceof MrItem.Namespace ns) {
                collectMrItems(moduleName, ns.members(), out);
            }
        }
    }

    private static ObrException duplicateDeclError(
            FunctionSignature sig, List<DeclaredFunction> matches) {
        StringBuilder sb = new StringBuilder("跨模块重复声明: ").append(sig).append("，命中模块:");
        for (DeclaredFunction m : matches) {
            sb.append("\n  ").append(m.moduleName()).append(".mr");
        }
        return new ObrException(sb.toString());
    }

    private static void checkCallsInBody(
            Path mainPath,
            String callerRel,
            ObrItem.DeRfunDef def,
            Map<FunctionSignature, List<DeclaredFunction>> decls,
            InterpreterAuditLog audit,
            String runId,
            Map<String, List<FileStaticRegistry.Slot>> fileStatics,
            FunctionSignature currentSig,
            ProgramLinkIndex linkIndex) {
        ScopeStack scopes = new ScopeStack();
        scopes.push();
        for (Map.Entry<String, List<FileStaticRegistry.Slot>> e : fileStatics.entrySet()) {
            List<FileStaticRegistry.Slot> foreign =
                    foreignSlots(e.getValue(), currentSig, mainPath, linkIndex, e.getKey());
            if (foreign.size() != 1) {
                /* 0：无外来 static；>1：裸名无法唯一确定所有者，不预注入，待实际引用时再报错。 */
                continue;
            }
            FileStaticRegistry.Slot slot = foreign.getFirst();
            scopes.declare(
                    mainPath,
                    e.getKey(),
                    new VarInfo(
                            slot.typeKeyword(),
                            true,
                            false,
                            slot.visibility()));
        }
        /* 参数与普通局部在更内层帧，便于遮蔽外层「文件 static」注入，避免与 var 同名冲突。 */
        scopes.push();
        for (ParamDecl p : def.params()) {
            scopes.declare(
                    mainPath,
                    p.name(),
                    new VarInfo(p.type().keywordLexeme(), false, true, VarVisibility.LOCAL));
        }
        String declaredRet = def.returnType().keywordLexeme();
        boolean voidFn = "void".equals(declaredRet);
        checkStmts(
                mainPath,
                callerRel,
                def.body().statements(),
                scopes,
                decls,
                audit,
                runId,
                voidFn,
                declaredRet,
                fileStatics,
                currentSig,
                linkIndex,
                0);
    }

    /**
     * 其它函数的 static 槽；跨 {@code .obr} 时仅包含 {@link VarVisibility#PUBLIC_STATIC}，同文件内可含
     * {@link VarVisibility#PRIVATE_STATIC}。
     */
    private static List<FileStaticRegistry.Slot> foreignSlots(
            List<FileStaticRegistry.Slot> slots,
            FunctionSignature currentSig,
            Path currentObrPath,
            ProgramLinkIndex linkIndex,
            String nameForError) {
        if (slots == null || slots.isEmpty()) {
            return List.of();
        }
        List<FileStaticRegistry.Slot> out = new ArrayList<>();
        for (FileStaticRegistry.Slot s : slots) {
            if (s.owner().equals(currentSig)) {
                continue;
            }
            if (sameObrFile(s.sourceObrPath(), currentObrPath)) {
                out.add(s);
            } else if (s.visibility() == VarVisibility.PUBLIC_STATIC) {
                if (linkIndex != null && !linkIndex.allowsAccess(currentObrPath, s.sourceObrPath())) {
                    throw new ObrException(
                            E_SEM_LINK_ACCESS_DENIED
                                    + " "
                                    + currentObrPath
                                    + ": #LINK 禁止从当前文件访问符号 "
                                    + nameForError
                                    + "（定义于 "
                                    + s.sourceObrPath()
                                    + "）");
                }
                out.add(s);
            }
        }
        return out;
    }

    private static boolean sameObrFile(Path a, Path b) {
        try {
            return a.toAbsolutePath().normalize().equals(b.toAbsolutePath().normalize());
        } catch (Exception e) {
            return a.equals(b);
        }
    }

    /**
     * 词法域解析后，再尝试「其它函数的 file static」。
     *
     * <p>多个 {@code deRfun} 可声明同名 static（类型须一致）；是否冲突在<strong>运行时</strong>根据「是否已有多处
     * 激活」判定，语义阶段不因此拒绝。
     */
    private static VarInfo resolveNameForUse(
            Path mainPath,
            String name,
            ScopeStack scopes,
            Map<String, List<FileStaticRegistry.Slot>> fileStatics,
            FunctionSignature currentSig,
            ProgramLinkIndex linkIndex) {
        VarInfo vi = scopes.resolve(name);
        if (vi != null) {
            return vi;
        }
        List<FileStaticRegistry.Slot> foreign =
                foreignSlots(fileStatics.getOrDefault(name, List.of()), currentSig, mainPath, linkIndex, name);
        if (foreign.isEmpty()) {
            return null;
        }
        String tk = foreign.getFirst().typeKeyword();
        for (FileStaticRegistry.Slot s : foreign) {
            if (!s.typeKeyword().equals(tk)) {
                throw new ObrException(
                        E_SEM_STATIC_FILE_DUP
                                + " "
                                + mainPath
                                + ": 文件级 static 同名但类型不一致: "
                                + name);
            }
        }
        return new VarInfo(tk, true, false, foreign.getFirst().visibility());
    }

    private static void checkStmts(
            Path mainPath,
            String callerRel,
            List<Stmt> stmts,
            ScopeStack scopes,
            Map<FunctionSignature, List<DeclaredFunction>> decls,
            InterpreterAuditLog audit,
            String runId,
            boolean voidFn,
            String declaredRet,
            Map<String, List<FileStaticRegistry.Slot>> fileStatics,
            FunctionSignature currentSig,
            ProgramLinkIndex linkIndex,
            int loopDepth) {
        for (Stmt s : stmts) {
            checkStmt(
                    mainPath,
                    callerRel,
                    s,
                    scopes,
                    decls,
                    audit,
                    runId,
                    voidFn,
                    declaredRet,
                    fileStatics,
                    currentSig,
                    linkIndex,
                    loopDepth);
        }
    }

    private static void checkStmt(
            Path mainPath,
            String callerRel,
            Stmt s,
            ScopeStack scopes,
            Map<FunctionSignature, List<DeclaredFunction>> decls,
            InterpreterAuditLog audit,
            String runId,
            boolean voidFn,
            String declaredRet,
            Map<String, List<FileStaticRegistry.Slot>> fileStatics,
            FunctionSignature currentSig,
            ProgramLinkIndex linkIndex,
            int loopDepth) {
        switch (s) {
            case Stmt.Expression exp ->
                    checkExprStmtExpr(
                            mainPath,
                            callerRel,
                            exp.expr(),
                            scopes,
                            decls,
                            audit,
                            runId,
                            fileStatics,
                            currentSig,
                            linkIndex,
                            0);
            case Stmt.Return ret -> {
                if (voidFn) {
                    throw new ObrException(E_SEM_RETURN_IN_VOID + " " + mainPath + ": void 函数不能使用 return");
                }
                String inferred =
                        inferType(
                                mainPath,
                                ret.value(),
                                scopes,
                                decls,
                                callerRel,
                                audit,
                                runId,
                                fileStatics,
                                linkIndex,
                                currentSig);
                if (!returnValueAssignableToDeclared(inferred, declaredRet)) {
                    throw new ObrException(
                            E_SEM_RETURN_VALUE_MISMATCH
                                    + " "
                                    + mainPath
                                    + ": return 表达式类型与函数头返回类型不一致: 推断="
                                    + inferred
                                    + " 声明="
                                    + declaredRet);
                }
            }
            case Stmt.VarDecl vd -> {
                boolean isStaticDecl = vd.visibility() != VarVisibility.LOCAL;
                String ty = vd.type().keywordLexeme();
                for (VarDeclarator d : vd.declarators()) {
                    scopes.declare(
                            mainPath,
                            d.name(),
                            new VarInfo(ty, isStaticDecl, false, vd.visibility()));
                    if (d.initOrNull() != null) {
                        String inf =
                                inferType(
                                        mainPath,
                                        d.initOrNull(),
                                        scopes,
                                        decls,
                                        callerRel,
                                        audit,
                                        runId,
                                        fileStatics,
                                        linkIndex,
                                        currentSig);
                        if (!returnValueAssignableToDeclared(inf, ty)) {
                            throw new ObrException(
                                    E_SEM_ASSIGN_TYPE
                                            + " "
                                            + mainPath
                                            + ": var 初值类型与声明不一致: "
                                            + d.name()
                                            + " 推断="
                                            + inf
                                            + " 声明="
                                            + ty);
                        }
                    }
                }
            }
            case Stmt.Block blk -> {
                scopes.push();
                try {
                    checkStmts(
                            mainPath,
                            callerRel,
                            blk.body().statements(),
                            scopes,
                            decls,
                            audit,
                            runId,
                            voidFn,
                            declaredRet,
                            fileStatics,
                            currentSig,
                            linkIndex,
                            loopDepth);
                } finally {
                    scopes.pop();
                }
            }
            case Stmt.Assign as ->
                    checkAssignSemantics(
                            mainPath,
                            callerRel,
                            as.name(),
                            as.op(),
                            as.value(),
                            scopes,
                            decls,
                            audit,
                            runId,
                            fileStatics,
                            currentSig,
                            linkIndex);
            case Stmt.Update up -> {
                VarInfo vi = resolveNameForUse(mainPath, up.name(), scopes, fileStatics, currentSig, linkIndex);
                if (vi == null) {
                    throw new ObrException(
                            E_SEM_TYPE_INFER_NAME + " " + mainPath + ": ++/-- 时未知标识符: " + up.name());
                }
                if (!isNumericPrimitive(vi.typeKeyword)) {
                    throw new ObrException(
                            E_SEM_TYPE_INFER_EXPR
                                    + " "
                                    + mainPath
                                    + ": ++/-- 仅适用于数值类型: "
                                    + up.name());
                }
            }
            case Stmt.If ifStmt -> {
                String tc =
                        inferType(
                                mainPath,
                                ifStmt.cond(),
                                scopes,
                                decls,
                                callerRel,
                                audit,
                                runId,
                                fileStatics,
                                linkIndex,
                                currentSig);
                if (!allowsLogicalNotOperand(tc)) {
                    throw new ObrException(
                            E_SEM_TYPE_INFER_EXPR + " " + mainPath + ": if 条件类型不可用于布尔上下文: " + tc);
                }
                checkStmt(
                        mainPath,
                        callerRel,
                        ifStmt.thenStmt(),
                        scopes,
                        decls,
                        audit,
                        runId,
                        voidFn,
                        declaredRet,
                        fileStatics,
                        currentSig,
                        linkIndex,
                        loopDepth);
                if (ifStmt.elseStmtOrNull() != null) {
                    checkStmt(
                            mainPath,
                            callerRel,
                            ifStmt.elseStmtOrNull(),
                            scopes,
                            decls,
                            audit,
                            runId,
                            voidFn,
                            declaredRet,
                            fileStatics,
                            currentSig,
                            linkIndex,
                            loopDepth);
                }
            }
            case Stmt.While wh -> {
                String tc =
                        inferType(
                                mainPath,
                                wh.cond(),
                                scopes,
                                decls,
                                callerRel,
                                audit,
                                runId,
                                fileStatics,
                                linkIndex,
                                currentSig);
                if (!allowsLogicalNotOperand(tc)) {
                    throw new ObrException(
                            E_SEM_TYPE_INFER_EXPR + " " + mainPath + ": while 条件类型不可用于布尔上下文: " + tc);
                }
                if (wh.body() instanceof Stmt.Block) {
                    checkStmt(
                            mainPath,
                            callerRel,
                            wh.body(),
                            scopes,
                            decls,
                            audit,
                            runId,
                            voidFn,
                            declaredRet,
                            fileStatics,
                            currentSig,
                            linkIndex,
                            loopDepth + 1);
                } else {
                    scopes.push();
                    try {
                        checkStmt(
                                mainPath,
                                callerRel,
                                wh.body(),
                                scopes,
                                decls,
                                audit,
                                runId,
                                voidFn,
                                declaredRet,
                                fileStatics,
                                currentSig,
                                linkIndex,
                                loopDepth + 1);
                    } finally {
                        scopes.pop();
                    }
                }
            }
            case Stmt.Break() -> {
                if (loopDepth <= 0) {
                    throw new ObrException(
                            E_SEM_BREAK_OUTSIDE_LOOP + " " + mainPath + ": break 仅允许出现在循环体内");
                }
            }
            case Stmt.Continue() -> {
                if (loopDepth <= 0) {
                    throw new ObrException(
                            E_SEM_CONTINUE_OUTSIDE_LOOP
                                    + " "
                                    + mainPath
                                    + ": continue 仅允许出现在循环体内");
                }
            }
            case Stmt.Nop n -> {}
            case Stmt.StaticMark sm -> {
                VarInfo vi = scopes.resolve(sm.name());
                if (vi == null) {
                    throw new ObrException(
                            E_SEM_STATIC_MARK_BAD + " " + mainPath + ": static 标记未找到变量: " + sm.name());
                }
                if (vi.isParam) {
                    throw new ObrException(
                            E_SEM_STATIC_MARK_BAD + " " + mainPath + ": static 标记不可用于形参: " + sm.name());
                }
                if (vi.isStatic) {
                    throw new ObrException(
                            E_SEM_STATIC_MARK_BAD + " " + mainPath + ": static 标记重复: " + sm.name());
                }
                vi.isStatic = true;
            }
        }
    }

    /** 实参类型经 {@link NumericWidening} 能落到形参/返回类型则视为可赋值。 */
    private static boolean returnValueAssignableToDeclared(String inferred, String declared) {
        if (inferred.equals(declared)) {
            return true;
        }
        if ("undefined".equals(inferred) && isPrimitiveTypeKeyword(declared)) {
            return true;
        }
        int c = NumericWidening.totalWideningCost(List.of(inferred), List.of(declared));
        return c >= 0;
    }

    private static boolean isPrimitiveTypeKeyword(String t) {
        return switch (t) {
            case "byte", "short", "int", "long", "float", "double", "char", "boolean", "string" -> true;
            default -> false;
        };
    }

    /** 语句级赋值与 {@link Expr.Assign} 共用。 */
    private static void checkAssignSemantics(
            Path mainPath,
            String callerRel,
            String name,
            Stmt.AssignOp op,
            Expr value,
            ScopeStack scopes,
            Map<FunctionSignature, List<DeclaredFunction>> decls,
            InterpreterAuditLog audit,
            String runId,
            Map<String, List<FileStaticRegistry.Slot>> fileStatics,
            FunctionSignature currentSig,
            ProgramLinkIndex linkIndex) {
        VarInfo vi = resolveNameForUse(mainPath, name, scopes, fileStatics, currentSig, linkIndex);
        if (vi == null) {
            throw new ObrException(E_SEM_TYPE_INFER_NAME + " " + mainPath + ": 赋值时未知标识符: " + name);
        }
        String inf =
                inferType(
                        mainPath,
                        value,
                        scopes,
                        decls,
                        callerRel,
                        audit,
                        runId,
                        fileStatics,
                        linkIndex,
                        currentSig);
        if (op == Stmt.AssignOp.ASSIGN) {
            if (!returnValueAssignableToDeclared(inf, vi.typeKeyword)) {
                throw new ObrException(
                        E_SEM_ASSIGN_TYPE
                                + " "
                                + mainPath
                                + ": 赋值类型不兼容: "
                                + name
                                + " 推断="
                                + inf
                                + " 声明="
                                + vi.typeKeyword);
            }
        } else if (op == Stmt.AssignOp.ADD_ASSIGN && "string".equals(vi.typeKeyword)) {
            if (!isConcatRhsForStringAddAssign(inf)) {
                throw new ObrException(
                        E_SEM_ASSIGN_TYPE
                                + " "
                                + mainPath
                                + ": string += 右侧类型不兼容: "
                                + name
                                + " 推断="
                                + inf);
            }
        } else {
            if ("string".equals(vi.typeKeyword) || "char".equals(vi.typeKeyword)) {
                throw new ObrException(
                        E_SEM_ASSIGN_TYPE
                                + " "
                                + mainPath
                                + ": 复合赋值（除 string +=）不可用于 string/char: "
                                + name);
            }
            if (!isNumericPrimitive(vi.typeKeyword)) {
                throw new ObrException(
                        E_SEM_ASSIGN_TYPE
                                + " "
                                + mainPath
                                + ": 复合赋值仅适用于数值类型: "
                                + name);
            }
            if (NumericExprTyping.isByte(vi.typeKeyword) && !"byte".equals(inf)) {
                throw new ObrException(
                        E_SEM_ASSIGN_TYPE
                                + " "
                                + mainPath
                                + ": byte 复合赋值右侧须为 byte: "
                                + name);
            }
            if (!NumericExprTyping.isByte(vi.typeKeyword) && !inf.equals(vi.typeKeyword)) {
                throw new ObrException(
                        E_SEM_ASSIGN_TYPE
                                + " "
                                + mainPath
                                + ": 复合赋值右侧类型须与变量一致: "
                                + name
                                + " 推断="
                                + inf
                                + " 声明="
                                + vi.typeKeyword);
            }
        }
    }

    /**
     * 表达式语句：校验整棵表达式；{@code void} 调用仅允许在<strong>顶层</strong>（深度 0），与 {@link #inferType}
     * 对 void 的限制一致。
     */
    private static void checkExprStmtExpr(
            Path mainPath,
            String callerRel,
            Expr e,
            ScopeStack scopes,
            Map<FunctionSignature, List<DeclaredFunction>> decls,
            InterpreterAuditLog audit,
            String runId,
            Map<String, List<FileStaticRegistry.Slot>> fileStatics,
            FunctionSignature currentSig,
            ProgramLinkIndex linkIndex,
            int depth) {
        switch (e) {
            case Expr.Literal lit -> {}
            case Expr.NameRef n -> {}
            case Expr.Assign a ->
                    checkAssignSemantics(
                            mainPath,
                            callerRel,
                            a.name(),
                            a.op(),
                            a.value(),
                            scopes,
                            decls,
                            audit,
                            runId,
                            fileStatics,
                            currentSig,
                            linkIndex);
            case Expr.Invoke inv -> {
                DeclaredFunction target =
                        resolveCallDeclaration(
                                mainPath,
                                callerRel,
                                inv.call(),
                                scopes,
                                decls,
                                audit,
                                runId,
                                fileStatics,
                                currentSig,
                                linkIndex);
                String ret = target.decl().returnType().keywordLexeme();
                if ("void".equals(ret) && depth > 0) {
                    throw new ObrException(
                            E_SEM_VOID_EXPR
                                    + " "
                                    + mainPath
                                    + ": void 函数调用不能作为表达式值: "
                                    + inv.call().callee());
                }
            }
            case Expr.Binary b -> {
                checkExprStmtExpr(
                        mainPath,
                        callerRel,
                        b.left(),
                        scopes,
                        decls,
                        audit,
                        runId,
                        fileStatics,
                        currentSig,
                        linkIndex,
                        depth + 1);
                checkExprStmtExpr(
                        mainPath,
                        callerRel,
                        b.right(),
                        scopes,
                        decls,
                        audit,
                        runId,
                        fileStatics,
                        currentSig,
                        linkIndex,
                        depth + 1);
            }
            case Expr.Unary u ->
                    checkExprStmtExpr(
                            mainPath,
                            callerRel,
                            u.operand(),
                            scopes,
                            decls,
                            audit,
                            runId,
                            fileStatics,
                            currentSig,
                            linkIndex,
                            depth + 1);
            case Expr.Conditional c -> {
                checkExprStmtExpr(
                        mainPath,
                        callerRel,
                        c.cond(),
                        scopes,
                        decls,
                        audit,
                        runId,
                        fileStatics,
                        currentSig,
                        linkIndex,
                        depth + 1);
                checkExprStmtExpr(
                        mainPath,
                        callerRel,
                        c.thenExpr(),
                        scopes,
                        decls,
                        audit,
                        runId,
                        fileStatics,
                        currentSig,
                        linkIndex,
                        depth + 1);
                checkExprStmtExpr(
                        mainPath,
                        callerRel,
                        c.elseExpr(),
                        scopes,
                        decls,
                        audit,
                        runId,
                        fileStatics,
                        currentSig,
                        linkIndex,
                        depth + 1);
            }
            case Expr.Postfix p ->
                    checkExprStmtExpr(
                            mainPath,
                            callerRel,
                            p.operand(),
                            scopes,
                            decls,
                            audit,
                            runId,
                            fileStatics,
                            currentSig,
                            linkIndex,
                            depth + 1);
            case Expr.PrefixUpdate p ->
                    checkExprStmtExpr(
                            mainPath,
                            callerRel,
                            p.operand(),
                            scopes,
                            decls,
                            audit,
                            runId,
                            fileStatics,
                            currentSig,
                            linkIndex,
                            depth + 1);
        }
    }

    private static void checkCall(
            Path mainPath,
            String callerRel,
            CallExpr call,
            ScopeStack scopes,
            Map<FunctionSignature, List<DeclaredFunction>> decls,
            InterpreterAuditLog audit,
            String runId,
            Map<String, List<FileStaticRegistry.Slot>> fileStatics,
            FunctionSignature currentSig,
            ProgramLinkIndex linkIndex) {
        resolveCallDeclaration(
                mainPath,
                callerRel,
                call,
                scopes,
                decls,
                audit,
                runId,
                fileStatics,
                currentSig,
                linkIndex);
    }

    /**
     * 解析调用到唯一声明；用于 {@link #checkCall} 与表达式 {@link Expr.Invoke} 的返回类型推断。
     */
    private static DeclaredFunction resolveCallDeclaration(
            Path mainPath,
            String callerRel,
            CallExpr call,
            ScopeStack scopes,
            Map<FunctionSignature, List<DeclaredFunction>> decls,
            InterpreterAuditLog audit,
            String runId,
            Map<String, List<FileStaticRegistry.Slot>> fileStatics,
            FunctionSignature currentSig,
            ProgramLinkIndex linkIndex) {
        List<String> argTypes = new ArrayList<>();
        for (Expr arg : call.arguments()) {
            argTypes.add(
                    inferType(
                            mainPath,
                            arg,
                            scopes,
                            decls,
                            callerRel,
                            audit,
                            runId,
                            fileStatics,
                            linkIndex,
                            currentSig));
        }
        String qn = String.join("::", call.callee().segments());
        FunctionSignature requested = new FunctionSignature(qn, List.copyOf(argTypes));
        List<DeclaredFunction> hits = decls.get(requested);
        FunctionSignature resolvedSig = requested;
        if (hits == null || hits.isEmpty()) {
            MatchResult mr = resolveByNumericWiden(qn, argTypes, decls);
            if (mr != null) {
                resolvedSig = mr.signature();
                hits = mr.matches();
            }
        }
        if (hits == null || hits.isEmpty()) {
            throw new ObrException(E_SEM_CALL_SIG_MISMATCH + " " + mainPath + ": 调用未匹配任何函数头（签名不匹配）: " + requested);
        }
        if (hits.size() > 1) {
            throw overloadAmbiguousError(mainPath, requested, hits);
        }
        DeclaredFunction target = hits.getFirst();
        audit.event(
                TraceLevel.NORMAL,
                TraceCategory.SEMANTIC,
                "bind_call_resolve",
                InterpreterAuditLog.fields(
                        "run_id", runId,
                        "caller_obr", mainPath.toString(),
                        "caller_rel", callerRel,
                        "callee", resolvedSig.toString(),
                        "decl_module", target.moduleName() + ".mr"));
        AccessRule callfun = accessRule(target.decl().attributes(), "Callfun", AccessRule.allowAll());
        if (!callfun.allows(callerRel)) {
            throw new ObrException(
                    E_SEM_CALLFUN_DENIED + " "
                            + mainPath
                            + ": 调用被 @Callfun 禁止: "
                            + resolvedSig
                            + "，caller="
                            + callerRel
                            + "，声明模块="
                            + target.moduleName()
                            + ".mr");
        }
        if (linkIndex != null) {
            Path implPath = linkIndex.defPathFor(resolvedSig);
            if (implPath != null && !sameObrFile(mainPath, implPath)) {
                if (!linkIndex.allowsAccess(mainPath, implPath)) {
                    throw new ObrException(
                            E_SEM_LINK_ACCESS_DENIED
                                    + " "
                                    + mainPath
                                    + ": #LINK 禁止调用 "
                                    + resolvedSig
                                    + "（定义于 "
                                    + implPath
                                    + "）");
                }
            }
        }
        return target;
    }

    private static MatchResult resolveByNumericWiden(
            String qn,
            List<String> argTypes,
            Map<FunctionSignature, List<DeclaredFunction>> decls) {
        int bestScore = Integer.MAX_VALUE;
        FunctionSignature bestSig = null;
        List<DeclaredFunction> bestMatches = null;
        for (Map.Entry<FunctionSignature, List<DeclaredFunction>> e : decls.entrySet()) {
            FunctionSignature cand = e.getKey();
            if (!cand.qualifiedName().equals(qn) || cand.paramTypes().size() != argTypes.size()) {
                continue;
            }
            int score = NumericWidening.totalWideningCost(argTypes, cand.paramTypes());
            if (score < 0) {
                continue;
            }
            if (score < bestScore) {
                bestScore = score;
                bestSig = cand;
                bestMatches = e.getValue();
            } else if (score == bestScore) {
                // 同分视为二义性，交由上层 matches.size()>1 触发冲突错误。
                if (bestMatches == null) {
                    bestMatches = new ArrayList<>();
                }
                bestMatches = new ArrayList<>(bestMatches);
                bestMatches.addAll(e.getValue());
            }
        }
        if (bestSig == null || bestMatches == null) {
            return null;
        }
        return new MatchResult(bestSig, bestMatches);
    }

    private record MatchResult(FunctionSignature signature, List<DeclaredFunction> matches) {}

    private static ObrException overloadAmbiguousError(
            Path mainPath, FunctionSignature requested, List<DeclaredFunction> hits) {
        StringBuilder sb = new StringBuilder(E_SEM_OVERLOAD_AMBIGUOUS + " " + mainPath + ": 调用重载二义性: ")
                .append(requested)
                .append("，候选声明:");
        for (DeclaredFunction h : hits) {
            FunctionSignature cand = FunctionSignature.of(h.decl().name(), h.decl().params());
            sb.append("\n  ")
                    .append(cand)
                    .append(" @ ")
                    .append(h.moduleName())
                    .append(".mr");
        }
        return new ObrException(sb.toString());
    }

    private static String inferType(
            Path mainPath,
            Expr arg,
            ScopeStack scopes,
            Map<FunctionSignature, List<DeclaredFunction>> decls,
            String callerRel,
            InterpreterAuditLog audit,
            String runId,
            Map<String, List<FileStaticRegistry.Slot>> fileStatics,
            ProgramLinkIndex linkIndex,
            FunctionSignature currentSig) {
        if (arg instanceof Expr.Literal lit) {
            String lex = lit.lexeme();
            if (lex.startsWith("\"")) {
                return "string";
            }
            if (lex.length() >= 2 && lex.startsWith("'") && lex.endsWith("'")) {
                try {
                    CharLiteralParser.parseCharLexeme(lex);
                    return "char";
                } catch (ObrException e) {
                    throw new ObrException(E_SEM_TYPE_INFER_LITERAL + " " + mainPath + ": " + e.getMessage());
                }
            }
            if (lex.equals("true") || lex.equals("false")) {
                return "boolean";
            }
            if (lex.equals("null")) {
                return "null";
            }
            if (lex.equals("undefined")) {
                return "undefined";
            }
            if (lex.matches("^[0-9]+[lL]$")) {
                return "long";
            }
            if (lex.matches("^[0-9]+\\.[0-9]+[fF]$")) {
                return "float";
            }
            if (lex.matches("^[0-9]+\\.[0-9]+([dD])?$")) {
                return "double";
            }
            if (lex.matches("^[0-9]+$")) {
                return "int";
            }
            throw new ObrException(E_SEM_TYPE_INFER_LITERAL + " " + mainPath + ": 无法推断字面量类型: " + lex);
        }
        if (arg instanceof Expr.NameRef n) {
            VarInfo vi = resolveNameForUse(mainPath, n.name(), scopes, fileStatics, currentSig, linkIndex);
            if (vi == null) {
                throw new ObrException(E_SEM_TYPE_INFER_NAME + " " + mainPath + ": 未知标识符（无法推断类型）: " + n.name());
            }
            return vi.typeKeyword;
        }
        if (arg instanceof Expr.Assign a) {
            checkAssignSemantics(
                    mainPath,
                    callerRel,
                    a.name(),
                    a.op(),
                    a.value(),
                    scopes,
                    decls,
                    audit,
                    runId,
                    fileStatics,
                    currentSig,
                    linkIndex);
            VarInfo vi = resolveNameForUse(mainPath, a.name(), scopes, fileStatics, currentSig, linkIndex);
            return vi.typeKeyword;
        }
        if (arg instanceof Expr.Invoke inv) {
            DeclaredFunction target =
                    resolveCallDeclaration(
                            mainPath,
                            callerRel,
                            inv.call(),
                            scopes,
                            decls,
                            audit,
                            runId,
                            fileStatics,
                            currentSig,
                            linkIndex);
            String ret = target.decl().returnType().keywordLexeme();
            if ("void".equals(ret)) {
                throw new ObrException(
                        E_SEM_VOID_EXPR + " " + mainPath + ": void 函数调用不能作为表达式值: " + inv.call().callee());
            }
            return ret;
        }
        if (arg instanceof Expr.Unary u) {
            return inferUnaryType(
                    mainPath, u, scopes, decls, callerRel, audit, runId, fileStatics, linkIndex, currentSig);
        }
        if (arg instanceof Expr.Conditional cond) {
            String tc =
                    inferType(
                            mainPath,
                            cond.cond(),
                            scopes,
                            decls,
                            callerRel,
                            audit,
                            runId,
                            fileStatics,
                            linkIndex,
                            currentSig);
            if (!allowsLogicalNotOperand(tc)) {
                throw new ObrException(
                        E_SEM_TYPE_INFER_EXPR + " " + mainPath + ": ?: 条件类型不可用于布尔上下文: " + tc);
            }
            String t1 =
                    inferType(
                            mainPath,
                            cond.thenExpr(),
                            scopes,
                            decls,
                            callerRel,
                            audit,
                            runId,
                            fileStatics,
                            linkIndex,
                            currentSig);
            String t2 =
                    inferType(
                            mainPath,
                            cond.elseExpr(),
                            scopes,
                            decls,
                            callerRel,
                            audit,
                            runId,
                            fileStatics,
                            linkIndex,
                            currentSig);
            if (!t1.equals(t2)) {
                throw new ObrException(
                        E_SEM_TYPE_INFER_EXPR
                                + " "
                                + mainPath
                                + ": ?: 两分支类型须一致: "
                                + t1
                                + " 与 "
                                + t2);
            }
            return t1;
        }
        if (arg instanceof Expr.Binary b) {
            return inferBinaryType(
                    mainPath, b, scopes, decls, callerRel, audit, runId, fileStatics, linkIndex, currentSig);
        }
        if (arg instanceof Expr.PrefixUpdate p) {
            return inferPrefixUpdateType(
                    mainPath, p, scopes, decls, callerRel, audit, runId, fileStatics, linkIndex, currentSig);
        }
        if (arg instanceof Expr.Postfix p) {
            return inferPostfixType(
                    mainPath, p, scopes, decls, callerRel, audit, runId, fileStatics, linkIndex, currentSig);
        }
        throw new ObrException(E_SEM_TYPE_INFER_EXPR + " " + mainPath + ": 不支持的实参表达式类型");
    }

    private static String inferUnaryType(
            Path mainPath,
            Expr.Unary u,
            ScopeStack scopes,
            Map<FunctionSignature, List<DeclaredFunction>> decls,
            String callerRel,
            InterpreterAuditLog audit,
            String runId,
            Map<String, List<FileStaticRegistry.Slot>> fileStatics,
            ProgramLinkIndex linkIndex,
            FunctionSignature currentSig) {
        String t =
                inferType(
                        mainPath,
                        u.operand(),
                        scopes,
                        decls,
                        callerRel,
                        audit,
                        runId,
                        fileStatics,
                        linkIndex,
                        currentSig);
        return switch (u.op()) {
            case POS, NEG -> {
                if (!isNumericPrimitive(t)) {
                    throw new ObrException(
                            E_SEM_TYPE_INFER_EXPR + " " + mainPath + ": 一元 +/- 须为数值类型，实际 " + t);
                }
                yield t;
            }
            case LNOT -> {
                if (!allowsLogicalNotOperand(t)) {
                    throw new ObrException(E_SEM_TYPE_INFER_EXPR + " " + mainPath + ": ! 的操作数类型不支持: " + t);
                }
                yield "boolean";
            }
            case BITNOT -> {
                if (!NumericExprTyping.isNumericPrimitive(t)) {
                    throw new ObrException(
                            E_SEM_TYPE_INFER_EXPR + " " + mainPath + ": ~ 须为数值类型，实际 " + t);
                }
                yield "int";
            }
        };
    }

    private static String inferBinaryType(
            Path mainPath,
            Expr.Binary b,
            ScopeStack scopes,
            Map<FunctionSignature, List<DeclaredFunction>> decls,
            String callerRel,
            InterpreterAuditLog audit,
            String runId,
            Map<String, List<FileStaticRegistry.Slot>> fileStatics,
            ProgramLinkIndex linkIndex,
            FunctionSignature currentSig) {
        String t1 =
                inferType(
                        mainPath,
                        b.left(),
                        scopes,
                        decls,
                        callerRel,
                        audit,
                        runId,
                        fileStatics,
                        linkIndex,
                        currentSig);
        String t2 =
                inferType(
                        mainPath,
                        b.right(),
                        scopes,
                        decls,
                        callerRel,
                        audit,
                        runId,
                        fileStatics,
                        linkIndex,
                        currentSig);
        return switch (b.op()) {
            case EQ, NE -> inferEqualityType(mainPath, b.op(), t1, t2);
            case LT, LE, GT, GE -> inferRelationalType(mainPath, t1, t2);
            case AND, OR -> inferLogicalAndOrType(mainPath, t1, t2);
            case POW -> inferPowType(mainPath, t1, t2);
            case ADD -> inferAddType(mainPath, t1, t2);
            case SUB, MUL, DIV, MOD -> inferArithmeticType(mainPath, b.op(), t1, t2);
            case BIT_AND, BIT_OR, BIT_XOR, SHL, SHR, USHR -> inferBitwiseOrShiftType(mainPath, t1, t2);
        };
    }

    /** 位运算与移位：结果类型 {@code int}（见 {@code docs/obr/operators.md} §7）。 */
    private static String inferBitwiseOrShiftType(Path mainPath, String t1, String t2) {
        if (!NumericExprTyping.isNumericPrimitive(t1) || !NumericExprTyping.isNumericPrimitive(t2)) {
            throw new ObrException(
                    E_SEM_TYPE_INFER_EXPR + " " + mainPath + ": 位运算/移位须为数值类型: " + t1 + " 与 " + t2);
        }
        if (NumericExprTyping.illegalByteMix(t1, t2)) {
            throw new ObrException(
                    E_SEM_TYPE_INFER_EXPR + " " + mainPath + ": 禁止 byte 与其它数值混用: " + t1 + " 与 " + t2);
        }
        return "int";
    }

    private static String inferEqualityType(
            Path mainPath, Expr.BinaryOp op, String t1, String t2) {
        if (!t1.equals(t2)) {
            throw new ObrException(
                    E_SEM_TYPE_INFER_EXPR + " " + mainPath + ": 相等比较两侧类型须一致: " + t1 + " 与 " + t2);
        }
        if (!equalityAllowedType(t1)) {
            throw new ObrException(E_SEM_TYPE_INFER_EXPR + " " + mainPath + ": ==/!= 不支持类型: " + t1);
        }
        return "boolean";
    }

    private static boolean equalityAllowedType(String t) {
        return switch (t) {
            case "byte", "short", "int", "long", "float", "double", "string", "char" -> true;
            default -> false;
        };
    }

    private static String inferRelationalType(Path mainPath, String t1, String t2) {
        if (!t1.equals(t2)) {
            throw new ObrException(
                    E_SEM_TYPE_INFER_EXPR + " " + mainPath + ": 关系运算两侧类型须一致: " + t1 + " 与 " + t2);
        }
        if (!relationalAllowedType(t1)) {
            throw new ObrException(E_SEM_TYPE_INFER_EXPR + " " + mainPath + ": 关系运算仅支持 byte/short/int/long，实际 " + t1);
        }
        return "boolean";
    }

    private static boolean relationalAllowedType(String t) {
        return switch (t) {
            case "byte", "short", "int", "long" -> true;
            default -> false;
        };
    }

    private static String inferLogicalAndOrType(Path mainPath, String t1, String t2) {
        if (!allowsLogicalNotOperand(t1) || !allowsLogicalNotOperand(t2)) {
            throw new ObrException(
                    E_SEM_TYPE_INFER_EXPR + " " + mainPath + ": &&/|| 操作数类型不支持: " + t1 + " / " + t2);
        }
        return "boolean";
    }

    private static String inferPowType(Path mainPath, String t1, String t2) {
        if (!NumericExprTyping.isNumericPrimitive(t1) || !NumericExprTyping.isNumericPrimitive(t2)) {
            throw new ObrException(E_SEM_TYPE_INFER_EXPR + " " + mainPath + ": ** 须为数值类型");
        }
        try {
            return NumericExprTyping.powResultType(t1, t2);
        } catch (IllegalArgumentException e) {
            throw new ObrException(
                    E_SEM_TYPE_INFER_EXPR + " " + mainPath + ": ** 禁止 byte 与其它数值混用: " + t1 + " 与 " + t2);
        }
    }

    private static String inferAddType(Path mainPath, String t1, String t2) {
        if (isStringConcatShape(t1, t2)) {
            if (concatenationForbidden(t1, t2)) {
                throw new ObrException(
                        E_SEM_TYPE_INFER_EXPR
                                + " "
                                + mainPath
                                + ": + 拼接不允许与 boolean/null/undefined 等混合: "
                                + t1
                                + " 与 "
                                + t2);
            }
            return "string";
        }
        if (!NumericExprTyping.isNumericPrimitive(t1) || !NumericExprTyping.isNumericPrimitive(t2)) {
            throw new ObrException(E_SEM_TYPE_INFER_EXPR + " " + mainPath + ": + 不支持类型: " + t1 + " 与 " + t2);
        }
        if (NumericExprTyping.illegalByteMix(t1, t2)) {
            throw new ObrException(
                    E_SEM_TYPE_INFER_EXPR + " " + mainPath + ": 禁止 byte 与其它数值混用: " + t1 + " 与 " + t2);
        }
        if (NumericExprTyping.isByte(t1) && NumericExprTyping.isByte(t2)) {
            return "byte";
        }
        try {
            return NumericExprTyping.promotedArithmeticType(t1, t2);
        } catch (IllegalArgumentException e) {
            throw new ObrException(
                    E_SEM_TYPE_INFER_EXPR + " " + mainPath + ": 数值 + 类型不兼容: " + t1 + " 与 " + t2);
        }
    }

    private static boolean isStringConcatShape(String t1, String t2) {
        return "string".equals(t1)
                || "string".equals(t2)
                || "char".equals(t1)
                || "char".equals(t2);
    }

    private static boolean concatenationForbidden(String t1, String t2) {
        if ("boolean".equals(t1)
                || "boolean".equals(t2)
                || "null".equals(t1)
                || "null".equals(t2)
                || "undefined".equals(t1)
                || "undefined".equals(t2)) {
            return true;
        }
        return !isConcatenationSideAllowed(t1) || !isConcatenationSideAllowed(t2);
    }

    private static boolean isConcatenationSideAllowed(String t) {
        return "string".equals(t)
                || "char".equals(t)
                || NumericExprTyping.isNumericPrimitive(t);
    }

    private static String inferArithmeticType(
            Path mainPath, Expr.BinaryOp op, String t1, String t2) {
        if (!NumericExprTyping.isNumericPrimitive(t1) || !NumericExprTyping.isNumericPrimitive(t2)) {
            throw new ObrException(E_SEM_TYPE_INFER_EXPR + " " + mainPath + ": 算术运算符须为数值类型");
        }
        if (NumericExprTyping.illegalByteMix(t1, t2)) {
            throw new ObrException(
                    E_SEM_TYPE_INFER_EXPR + " " + mainPath + ": 禁止 byte 与其它数值混用: " + t1 + " 与 " + t2);
        }
        if (NumericExprTyping.isByte(t1) && NumericExprTyping.isByte(t2)) {
            return "byte";
        }
        try {
            return NumericExprTyping.promotedArithmeticType(t1, t2);
        } catch (IllegalArgumentException e) {
            throw new ObrException(
                    E_SEM_TYPE_INFER_EXPR + " " + mainPath + ": 运算符 " + op + " 类型不兼容: " + t1 + " 与 " + t2);
        }
    }

    private static String inferPrefixUpdateType(
            Path mainPath,
            Expr.PrefixUpdate p,
            ScopeStack scopes,
            Map<FunctionSignature, List<DeclaredFunction>> decls,
            String callerRel,
            InterpreterAuditLog audit,
            String runId,
            Map<String, List<FileStaticRegistry.Slot>> fileStatics,
            ProgramLinkIndex linkIndex,
            FunctionSignature currentSig) {
        if (!(p.operand() instanceof Expr.NameRef n)) {
            throw new ObrException(
                    E_SEM_TYPE_INFER_EXPR + " " + mainPath + ": 前缀 ++/-- 仅适用于变量");
        }
        VarInfo vi = resolveNameForUse(mainPath, n.name(), scopes, fileStatics, currentSig, linkIndex);
        if (vi == null) {
            throw new ObrException(
                    E_SEM_TYPE_INFER_NAME + " " + mainPath + ": 前缀 ++/-- 时未知标识符: " + n.name());
        }
        if (!isNumericPrimitive(vi.typeKeyword)) {
            throw new ObrException(
                    E_SEM_TYPE_INFER_EXPR + " " + mainPath + ": 前缀 ++/-- 仅适用于数值类型: " + n.name());
        }
        return vi.typeKeyword;
    }

    private static String inferPostfixType(
            Path mainPath,
            Expr.Postfix p,
            ScopeStack scopes,
            Map<FunctionSignature, List<DeclaredFunction>> decls,
            String callerRel,
            InterpreterAuditLog audit,
            String runId,
            Map<String, List<FileStaticRegistry.Slot>> fileStatics,
            ProgramLinkIndex linkIndex,
            FunctionSignature currentSig) {
        if (!(p.operand() instanceof Expr.NameRef n)) {
            throw new ObrException(
                    E_SEM_TYPE_INFER_EXPR + " " + mainPath + ": ++/-- 仅适用于变量");
        }
        VarInfo vi = resolveNameForUse(mainPath, n.name(), scopes, fileStatics, currentSig, linkIndex);
        if (vi == null) {
            throw new ObrException(
                    E_SEM_TYPE_INFER_NAME + " " + mainPath + ": ++/-- 时未知标识符: " + n.name());
        }
        if (!isNumericPrimitive(vi.typeKeyword)) {
            throw new ObrException(
                    E_SEM_TYPE_INFER_EXPR + " " + mainPath + ": ++/-- 仅适用于数值类型: " + n.name());
        }
        return vi.typeKeyword;
    }

    private static boolean isConcatRhsForStringAddAssign(String inf) {
        return "string".equals(inf)
                || "char".equals(inf)
                || NumericExprTyping.isNumericPrimitive(inf);
    }

    private static boolean allowsLogicalNotOperand(String t) {
        return "boolean".equals(t)
                || isNumericPrimitive(t)
                || "string".equals(t)
                || "char".equals(t)
                || "null".equals(t)
                || "undefined".equals(t);
    }

    private static boolean isNumericPrimitive(String t) {
        return NumericExprTyping.isNumericPrimitive(t);
    }

    /**
     * @param whenAbsent 未出现该特性时的规则（moduleR 7.3/7.4：无 {@code @Overwrite} 等价 {@code @Overwrite()}；
     *                   无 {@code @Callfun} 等价 {@code @Callfun(*)}，二者语义上均为「允许全部」）
     */
    private static AccessRule accessRule(List<Attribute> attrs, String attrName, AccessRule whenAbsent) {
        for (Attribute a : attrs) {
            if (a.name().equals(attrName)) {
                return AccessRule.parse(a.rawInsideParens(), whenAbsent);
            }
        }
        return whenAbsent;
    }

    private static String normalizedRelPath(Path projectRoot, Path file) {
        Path absRoot = projectRoot.toAbsolutePath().normalize();
        Path absFile = file.toAbsolutePath().normalize();
        Path rel = absRoot.relativize(absFile);
        return "/" + rel.toString().replace('\\', '/');
    }

    private record AccessRule(List<Pattern> allow, List<Pattern> deny, boolean hasAllow) {
        static AccessRule allowAll() {
            return new AccessRule(List.of(Pattern.compile(".*")), List.of(), true);
        }

        static AccessRule parse(String rawInsideParens, AccessRule defaultWhenEmpty) {
            if (rawInsideParens == null || rawInsideParens.isEmpty()) {
                return defaultWhenEmpty;
            }
            String[] parts = rawInsideParens.split(",");
            List<Pattern> allow = new ArrayList<>();
            List<Pattern> deny = new ArrayList<>();
            for (String p : parts) {
                if (p == null || p.isBlank()) {
                    continue;
                }
                boolean neg = p.startsWith("!");
                String rule = neg ? p.substring(1) : p;
                Pattern compiled = compileRule(rule);
                if (neg) {
                    deny.add(compiled);
                } else {
                    allow.add(compiled);
                }
            }
            if (allow.isEmpty() && deny.isEmpty()) {
                return defaultWhenEmpty;
            }
            return new AccessRule(List.copyOf(allow), List.copyOf(deny), !allow.isEmpty());
        }

        boolean allows(String relPath) {
            for (Pattern p : deny) {
                if (p.matcher(relPath).matches()) {
                    return false;
                }
            }
            if (!hasAllow) {
                return true;
            }
            for (Pattern p : allow) {
                if (p.matcher(relPath).matches()) {
                    return true;
                }
            }
            return false;
        }

        private static Pattern compileRule(String rule) {
            String normalized = rule.replace('\\', '/');
            if (!normalized.startsWith("/")) {
                normalized = "/" + normalized;
            }
            if (normalized.endsWith("/") && !normalized.endsWith("*/")) {
                normalized += "*";
            }
            StringBuilder sb = new StringBuilder("^");
            for (int i = 0; i < normalized.length(); i++) {
                char c = normalized.charAt(i);
                if (c == '*' && i + 1 < normalized.length() && normalized.charAt(i + 1) == '/') {
                    // "*/x" 允许直接命中 "/x"（零段）或任意前缀目录。
                    sb.append("(?:.*/)?");
                    i++;
                } else if (c == '*') {
                    sb.append(".*");
                } else if ("\\.[]{}()+-^$|?".indexOf(c) >= 0) {
                    sb.append('\\').append(c);
                } else {
                    sb.append(c);
                }
            }
            sb.append("$");
            return Pattern.compile(sb.toString());
        }
    }
}
