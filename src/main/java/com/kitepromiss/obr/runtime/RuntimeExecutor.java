package com.kitepromiss.obr.runtime;

import com.kitepromiss.obr.ObrException;
import com.kitepromiss.obr.ast.CallExpr;
import com.kitepromiss.obr.ast.Expr;
import com.kitepromiss.obr.ast.ObrFile;
import com.kitepromiss.obr.ast.ObrItem;
import com.kitepromiss.obr.ast.ParamDecl;
import com.kitepromiss.obr.ast.Stmt;
import com.kitepromiss.obr.ast.VarDeclarator;
import com.kitepromiss.obr.ast.VarVisibility;
import com.kitepromiss.obr.lex.CharLiteralParser;
import com.kitepromiss.obr.module.ObrProgramBundle;
import com.kitepromiss.obr.semantic.FileStaticRegistry;
import com.kitepromiss.obr.semantic.FunctionSignature;
import com.kitepromiss.obr.semantic.NumericExprTyping;
import com.kitepromiss.obr.semantic.NumericWidening;
import com.kitepromiss.obr.trace.InterpreterAuditLog;
import com.kitepromiss.obr.trace.TraceCategory;
import com.kitepromiss.obr.trace.TraceLevel;

import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** P5 最小运行时：main 入口、调用栈、std::rout 原生代理。 */
public final class RuntimeExecutor {
    private static final String E_RT_ROUT_ARG = "E_RT_ROUT_ARG";
    private static final String E_RT_IMPL_NOT_FOUND = "E_RT_IMPL_NOT_FOUND";
    private static final String E_RT_STACK_OVERFLOW = "E_RT_STACK_OVERFLOW";
    private static final String E_RT_OVERLOAD_AMBIGUOUS = "E_RT_OVERLOAD_AMBIGUOUS";
    private static final String E_RT_LITERAL_UNSUPPORTED = "E_RT_LITERAL_UNSUPPORTED";
    private static final String E_RT_NAME_UNKNOWN = "E_RT_NAME_UNKNOWN";
    private static final String E_RT_EXPR_UNSUPPORTED = "E_RT_EXPR_UNSUPPORTED";
    private static final String E_RT_DUP_DEF = "E_RT_DUP_DEF";
    private static final String E_RT_RETURN_MISSING = "E_RT_RETURN_MISSING";
    private static final String E_RT_RETURN_COERCE = "E_RT_RETURN_COERCE";
    private static final String E_RT_STATIC_FILE_DUP = "E_RT_STATIC_FILE_DUP";
    /** 同一函数再次执行 {@code static var} 且同名槽已存在（含二次调用、递归重入）。 */
    private static final String E_RT_STATIC_LOCAL_REDECL = "E_RT_STATIC_LOCAL_REDECL";
    private static final String E_RT_COMPOUND_UNINIT = "E_RT_COMPOUND_UNINIT";
    private static final String E_RT_INTEGER_DIV_ZERO = "E_RT_INTEGER_DIV_ZERO";

    private final InterpreterAuditLog audit;
    private final PrintStream out;
    private final Map<FunctionSignature, ObrItem.DeRfunDef> defs;
    private final Map<FunctionSignature, String> defOrigins;
    private final ArrayDeque<Frame> stack = new ArrayDeque<>();
    /**
     * 每函数签名一份 static 变量（值 + 声明类型，跨调用持久）。{@code static var} 在<strong>每次</strong>执行到该语句时
     * 若同名槽已存在则 {@link #E_RT_STATIC_LOCAL_REDECL}（与 C 风格「仅初始化一次」不同）。
     */
    private final Map<FunctionSignature, StaticStore> functionStaticStores = new HashMap<>();
    /**
     * 合并后的文件级 static 登记（可多 {@link FileStaticRegistry.Slot} 同名）。
     * 槽仅在对应函数执行到 {@code static} 声明后存在；多处同名均已激活时读/写裸名 {@link #E_RT_STATIC_FILE_DUP}。
     */
    private final Map<String, List<FileStaticRegistry.Slot>> fileStaticSlotsByName = new HashMap<>();

    private static final class StaticStore {
        final Map<String, Value> values = new HashMap<>();
        final Map<String, String> types = new HashMap<>();
    }
    private final int maxCallDepth;
    private final String runId;

    public RuntimeExecutor(InterpreterAuditLog audit, PrintStream out) {
        this(audit, out, 1024, "n/a");
    }

    public RuntimeExecutor(InterpreterAuditLog audit, PrintStream out, int maxCallDepth) {
        this(audit, out, maxCallDepth, "n/a");
    }

    public RuntimeExecutor(InterpreterAuditLog audit, PrintStream out, int maxCallDepth, String runId) {
        this.audit = audit;
        this.out = out;
        this.defs = new HashMap<>();
        this.defOrigins = new HashMap<>();
        this.maxCallDepth = maxCallDepth;
        this.runId = runId;
    }

    public void executeMain(ObrFile obr) {
        executeMain(List.of(obr));
    }

    public void executeMain(ObrProgramBundle program) {
        defs.clear();
        defOrigins.clear();
        functionStaticStores.clear();
        fileStaticSlotsByName.clear();
        for (ObrProgramBundle.ParsedObrFile f : program.files()) {
            mergeFileStaticRegistry(f.ast(), f.path());
        }
        for (ObrProgramBundle.ParsedObrFile f : program.files()) {
            String origin = f.path().toString();
            for (ObrItem item : f.ast().items()) {
                if (item instanceof ObrItem.DeRfunDef def) {
                    FunctionSignature sig = FunctionSignature.of(def.name(), def.params());
                    if (defs.containsKey(sig)) {
                        throw new ObrException(
                                E_RT_DUP_DEF
                                        + " 运行时合并定义时发现重复签名（语义阶段应已拦截）: "
                                        + sig
                                        + " 已有="
                                        + defOrigins.get(sig)
                                        + " 再次="
                                        + origin);
                    }
                    defs.put(sig, def);
                    defOrigins.put(sig, origin);
                }
            }
        }
        call("main", List.of(), program.mainPath().toString());
    }

    public void executeMain(List<ObrFile> allObrFiles) {
        defs.clear();
        defOrigins.clear();
        functionStaticStores.clear();
        fileStaticSlotsByName.clear();
        int idx = 0;
        for (ObrFile obr : allObrFiles) {
            mergeFileStaticRegistry(obr, Path.of("obr[" + idx++ + "].obr"));
        }
        for (ObrFile obr : allObrFiles) {
            for (ObrItem item : obr.items()) {
                if (item instanceof ObrItem.DeRfunDef def) {
                    FunctionSignature sig = FunctionSignature.of(def.name(), def.params());
                    if (defs.containsKey(sig)) {
                        throw new ObrException(
                                E_RT_DUP_DEF + " 重复的 deRfun 定义: " + sig + "（多文件列表模式）");
                    }
                    defs.put(sig, def);
                    defOrigins.put(sig, "<unknown>");
                }
            }
        }
        call("main", List.of(), "<unknown>");
    }

    private void mergeFileStaticRegistry(ObrFile obr, Path path) {
        Map<String, List<FileStaticRegistry.Slot>> part = FileStaticRegistry.collect(obr, path);
        for (Map.Entry<String, List<FileStaticRegistry.Slot>> e : part.entrySet()) {
            fileStaticSlotsByName
                    .computeIfAbsent(e.getKey(), k -> new ArrayList<>())
                    .addAll(e.getValue());
        }
    }

    /**
     * 语句序列执行结果：正常结束、{@code return}、或对 void 的<strong>尾调用</strong>请求（不增加 JVM 栈，
     * 由 {@link #call} 外层循环承接）。
     */
    private sealed interface StmtExecResult
            permits StmtExecResult.Done, StmtExecResult.ReturnedVal, StmtExecResult.TailVoidCall {
        record Done() implements StmtExecResult {}

        record ReturnedVal(Value value) implements StmtExecResult {}

        record TailVoidCall(String qn, List<Value> args, String callerFile) implements StmtExecResult {}
    }

    private record LocalBinding(String typeKeyword, Value value) {}

    /** 块环境与词法作用域链；{@link #getValue} 在局部链之后查函数 {@link StaticStore}。 */
    private static final class Env {
        private final List<Map<String, Value>> scopes = new ArrayList<>();
        private final List<Map<String, String>> typeScopes = new ArrayList<>();

        void push() {
            scopes.add(new HashMap<>());
            typeScopes.add(new HashMap<>());
        }

        void pop() {
            scopes.removeLast();
            typeScopes.removeLast();
        }

        void putLocal(String name, String typeKeyword, Value v) {
            scopes.getLast().put(name, v);
            typeScopes.getLast().put(name, typeKeyword);
        }

        Value getValue(String name, StaticStore statics) {
            for (int i = scopes.size() - 1; i >= 0; i--) {
                Map<String, Value> m = scopes.get(i);
                if (m.containsKey(name)) {
                    return m.get(name);
                }
            }
            if (statics != null && statics.values.containsKey(name)) {
                return statics.values.get(name);
            }
            return null;
        }

        String getDeclaredType(String name, StaticStore statics) {
            for (int i = scopes.size() - 1; i >= 0; i--) {
                Map<String, String> m = typeScopes.get(i);
                if (m.containsKey(name)) {
                    return m.get(name);
                }
            }
            if (statics != null && statics.types.containsKey(name)) {
                return statics.types.get(name);
            }
            return null;
        }

        LocalBinding removeFromInnermost(String name) {
            for (int i = scopes.size() - 1; i >= 0; i--) {
                Map<String, Value> vs = scopes.get(i);
                if (vs.containsKey(name)) {
                    Value v = vs.remove(name);
                    String t = typeScopes.get(i).remove(name);
                    return new LocalBinding(t, v);
                }
            }
            return null;
        }

        boolean tryAssign(String name, Value v, StaticStore statics) {
            for (int i = scopes.size() - 1; i >= 0; i--) {
                Map<String, Value> m = scopes.get(i);
                if (m.containsKey(name)) {
                    m.put(name, v);
                    return true;
                }
            }
            if (statics != null && statics.values.containsKey(name)) {
                statics.values.put(name, v);
                return true;
            }
            return false;
        }

        void assign(String name, Value v, StaticStore statics) {
            if (!tryAssign(name, v, statics)) {
                throw new ObrException(E_RT_NAME_UNKNOWN + " 赋值时未知变量: " + name);
            }
        }
    }

    /**
     * 调用函数；{@code std::rout} 与 void 用户函数返回 {@code null}，非 void 用户函数返回 {@link Value}。
     */
    private Value call(String qn, List<Value> args, String callerFile) {
        if ("std::rout".equals(qn)) {
            if (args.size() != 1) {
                throw new ObrException(E_RT_ROUT_ARG + " std::rout 须恰好一个参数");
            }
            Value v = args.getFirst();
            audit.event(
                    TraceLevel.NORMAL,
                    TraceCategory.RUNTIME,
                    "call_resolve",
                    InterpreterAuditLog.fields(
                            "run_id", runId,
                            "caller_file", callerFile,
                            "callee", qn,
                            "defined_in", "libs/system.obr(native)"));
            if (v.type() == ValueType.STRING) {
                out.println(v.asString());
                return null;
            }
            if (v.type() == ValueType.CHAR) {
                out.println(String.valueOf(v.charValue()));
                return null;
            }
            if (v.type() == ValueType.INT) {
                out.println(Integer.toString(v.intValue()));
                return null;
            }
            if (v.type() == ValueType.LONG) {
                out.println(Long.toString(v.longValue()));
                return null;
            }
            if (v.type() == ValueType.FLOAT) {
                out.println(Float.toString(v.floatValue()));
                return null;
            }
            if (v.type() == ValueType.DOUBLE) {
                out.println(Double.toString(v.doubleValue()));
                return null;
            }
            throw new ObrException(
                    E_RT_ROUT_ARG
                            + " std::rout 仅接受 string、char、整型/浮点标量等可打印类型（见 system.mr 声明）");
        }

        String nextQn = qn;
        List<Value> nextArgs = args;
        String nextCaller = callerFile;

        while (true) {
            List<String> actualTypes = nextArgs.stream().map(a -> a.type().keyword).toList();
            FunctionSignature requested = new FunctionSignature(nextQn, actualTypes);
            FunctionSignature resolved = resolveRuntimeSignature(requested);
            ObrItem.DeRfunDef def = defs.get(resolved);
            if (def == null) {
                throw new ObrException(
                        E_RT_IMPL_NOT_FOUND
                                + " 运行时未找到函数实现: "
                                + requested
                                + "，调用链="
                                + formatStackWith(nextQn));
            }
            String calleeFileResolved = defOrigins.getOrDefault(resolved, "<unknown>");
            if (stack.size() >= maxCallDepth) {
                throw new ObrException(
                        E_RT_STACK_OVERFLOW
                                + " "
                                + "调用栈深度超限: depth="
                                + stack.size()
                                + ", max="
                                + maxCallDepth
                                + ", next="
                                + nextQn
                                + ", 调用链="
                                + formatStackWith(nextQn));
            }

            audit.event(
                    TraceLevel.NORMAL,
                    TraceCategory.RUNTIME,
                    "call_resolve",
                    InterpreterAuditLog.fields(
                            "run_id", runId,
                            "caller_file", nextCaller,
                            "callee", resolved.qualifiedName(),
                            "defined_in", calleeFileResolved));
            stack.push(new Frame(nextQn, calleeFileResolved));
            audit.event(
                    TraceLevel.NORMAL,
                    TraceCategory.RUNTIME,
                    "call_enter",
                    InterpreterAuditLog.fields(
                            "run_id", runId,
                            "callee", resolved.toString(),
                            "stack_depth", Integer.toString(stack.size())));

            StaticStore statics = functionStaticStores.computeIfAbsent(resolved, k -> new StaticStore());
            Env env = new Env();
            env.push();
            for (int i = 0; i < def.params().size(); i++) {
                ParamDecl p = def.params().get(i);
                env.putLocal(p.name(), p.type().keywordLexeme(), nextArgs.get(i));
            }
            String declaredRet = def.returnType().keywordLexeme();
            boolean voidFn = "void".equals(declaredRet);
            StmtExecResult ser =
                    executeStmtsWithTail(
                            def.body().statements(), env, statics, calleeFileResolved, declaredRet, voidFn);

            audit.event(
                    TraceLevel.NORMAL,
                    TraceCategory.RUNTIME,
                    "call_exit",
                    InterpreterAuditLog.fields(
                            "run_id", runId,
                            "callee", resolved.toString(),
                            "stack_depth", Integer.toString(stack.size())));
            stack.pop();

            switch (ser) {
                case StmtExecResult.TailVoidCall t -> {
                    nextQn = t.qn();
                    nextArgs = t.args();
                    nextCaller = t.callerFile();
                }
                case StmtExecResult.ReturnedVal rv -> {
                    return rv.value();
                }
                case StmtExecResult.Done d -> {
                    if (!voidFn) {
                        throw new ObrException(
                                E_RT_RETURN_MISSING + " 非 void 函数执行结束但未执行 return: " + resolved);
                    }
                    return null;
                }
            }
        }
    }

    /**
     * 执行语句列表；void 函数<strong>块尾</strong>对另一 void 函数的直调用可走尾调用，不消耗 JVM 调用栈。
     */
    private StmtExecResult executeStmtsWithTail(
            List<Stmt> stmts,
            Env env,
            StaticStore statics,
            String calleeFile,
            String declaredRet,
            boolean voidFn) {
        for (int i = 0; i < stmts.size(); i++) {
            Stmt s = stmts.get(i);
            boolean last = i == stmts.size() - 1;

            if (s instanceof Stmt.Block blk) {
                env.push();
                try {
                    StmtExecResult br =
                            executeStmtsWithTail(
                                    blk.body().statements(), env, statics, calleeFile, declaredRet, voidFn);
                    if (br instanceof StmtExecResult.TailVoidCall || br instanceof StmtExecResult.ReturnedVal) {
                        return br;
                    }
                } finally {
                    env.pop();
                }
                continue;
            }

            if (last && voidFn && s instanceof Stmt.Expression se) {
                Optional<StmtExecResult.TailVoidCall> tail =
                        tryResolveVoidTailCall(se.call(), env, statics, calleeFile);
                if (tail.isPresent()) {
                    return tail.get();
                }
            }

            StmtExecResult r = executeStmtWithoutBlock(s, env, statics, calleeFile, declaredRet, voidFn);
            if (r instanceof StmtExecResult.ReturnedVal) {
                return r;
            }
            if (r instanceof StmtExecResult.TailVoidCall) {
                return r;
            }
        }
        return new StmtExecResult.Done();
    }

    private StmtExecResult executeBranch(
            Stmt s, Env env, StaticStore statics, String calleeFile, String declaredRet, boolean voidFn) {
        if (s instanceof Stmt.Block blk) {
            env.push();
            try {
                return executeStmtsWithTail(blk.body().statements(), env, statics, calleeFile, declaredRet, voidFn);
            } finally {
                env.pop();
            }
        }
        return executeStmtWithoutBlock(s, env, statics, calleeFile, declaredRet, voidFn);
    }

    private Optional<StmtExecResult.TailVoidCall> tryResolveVoidTailCall(
            CallExpr call, Env env, StaticStore statics, String callerFile) {
        String qn = String.join("::", call.callee().segments());
        if ("std::rout".equals(qn)) {
            return Optional.empty();
        }
        List<Value> evaled =
                call.arguments().stream().map(a -> evalExpr(a, env, statics, callerFile)).toList();
        List<String> actualTypes = evaled.stream().map(a -> a.type().keyword).toList();
        FunctionSignature requested = new FunctionSignature(qn, actualTypes);
        FunctionSignature resolved = resolveRuntimeSignature(requested);
        ObrItem.DeRfunDef def = defs.get(resolved);
        if (def == null || !"void".equals(def.returnType().keywordLexeme())) {
            return Optional.empty();
        }
        return Optional.of(new StmtExecResult.TailVoidCall(qn, evaled, callerFile));
    }

    private StmtExecResult executeStmtWithoutBlock(
            Stmt s, Env env, StaticStore statics, String calleeFile, String declaredRet, boolean voidFn) {
        switch (s) {
            case Stmt.Expression exp -> {
                executeCall(exp.call(), env, statics, calleeFile);
                return new StmtExecResult.Done();
            }
            case Stmt.Return ret -> {
                if (voidFn) {
                    throw new ObrException(E_RT_EXPR_UNSUPPORTED + " void 函数不应出现 return（语义阶段应已拦截）");
                }
                Value raw = evalExpr(ret.value(), env, statics, calleeFile);
                return new StmtExecResult.ReturnedVal(widenReturnValue(declaredRet, raw));
            }
            case Stmt.VarDecl vd -> {
                executeVarDecl(vd, env, statics, calleeFile);
                return new StmtExecResult.Done();
            }
            case Stmt.Block ignored -> throw new AssertionError("Block handled in executeStmtsWithTail");
            case Stmt.Assign as -> {
                String ty = resolveDeclaredType(as.name(), env, statics, calleeFile);
                if (ty == null) {
                    throw new ObrException(E_RT_NAME_UNKNOWN + " 赋值时未知变量: " + as.name());
                }
                if (as.op() == Stmt.AssignOp.ASSIGN) {
                    Value rhs = evalExpr(as.value(), env, statics, calleeFile);
                    assignWithForeign(as.name(), widenReturnValue(ty, rhs), env, statics, calleeFile);
                    return new StmtExecResult.Done();
                }
                if (as.op() == Stmt.AssignOp.ADD_ASSIGN && "string".equals(ty)) {
                    Value cur = getValueForName(as.name(), env, statics, calleeFile);
                    if (cur == null || cur.type() == ValueType.UNDEFINED) {
                        throw new ObrException(E_RT_COMPOUND_UNINIT + " string += 时变量未初始化: " + as.name());
                    }
                    if (cur.type() != ValueType.STRING) {
                        throw new ObrException(E_RT_EXPR_UNSUPPORTED + " string += 期望 string");
                    }
                    Value rhs = evalExpr(as.value(), env, statics, calleeFile);
                    assignWithForeign(
                            as.name(),
                            Value.ofString(cur.asString() + valueToConcatString(rhs)),
                            env,
                            statics,
                            calleeFile);
                    return new StmtExecResult.Done();
                }
                Value cur = requireNumericForCompound(as.name(), env, statics, calleeFile);
                Value rhs = evalExpr(as.value(), env, statics, calleeFile);
                if (cur.type() != rhs.type()) {
                    throw new ObrException(E_RT_EXPR_UNSUPPORTED + " 复合赋值两侧运行时类型须一致");
                }
                Value combined =
                        switch (as.op()) {
                            case ADD_ASSIGN -> evalArithmetic(cur, rhs, 0);
                            case SUB_ASSIGN -> evalArithmetic(cur, rhs, 1);
                            case MUL_ASSIGN -> evalArithmetic(cur, rhs, 2);
                            case DIV_ASSIGN -> evalDiv(cur, rhs);
                            case MOD_ASSIGN -> evalMod(cur, rhs);
                            case ASSIGN -> throw new AssertionError();
                        };
                assignWithForeign(as.name(), widenReturnValue(ty, combined), env, statics, calleeFile);
                return new StmtExecResult.Done();
            }
            case Stmt.Update up -> {
                String ty = resolveDeclaredType(up.name(), env, statics, calleeFile);
                if (ty == null) {
                    throw new ObrException(E_RT_NAME_UNKNOWN + " ++/-- 时未知变量: " + up.name());
                }
                Value cur = requireNumericForCompound(up.name(), env, statics, calleeFile);
                Value one = literalOneMatching(cur);
                boolean incr =
                        up.kind() == Stmt.UpdateKind.PREFIX_INCR
                                || up.kind() == Stmt.UpdateKind.POSTFIX_INCR;
                Value next = incr ? evalArithmetic(cur, one, 0) : evalArithmetic(cur, one, 1);
                assignWithForeign(up.name(), widenReturnValue(ty, next), env, statics, calleeFile);
                return new StmtExecResult.Done();
            }
            case Stmt.If ifs -> {
                Value c = evalExpr(ifs.cond(), env, statics, calleeFile);
                if (truthy(c)) {
                    return executeBranch(ifs.thenStmt(), env, statics, calleeFile, declaredRet, voidFn);
                } else if (ifs.elseStmtOrNull() != null) {
                    return executeBranch(ifs.elseStmtOrNull(), env, statics, calleeFile, declaredRet, voidFn);
                }
                return new StmtExecResult.Done();
            }
            case Stmt.Nop n -> {
                return new StmtExecResult.Done();
            }
            case Stmt.StaticMark sm -> {
                LocalBinding lb = env.removeFromInnermost(sm.name());
                if (lb == null) {
                    throw new ObrException(E_RT_NAME_UNKNOWN + " static 标记未找到局部变量: " + sm.name());
                }
                statics.values.put(sm.name(), lb.value());
                statics.types.put(sm.name(), lb.typeKeyword());
                return new StmtExecResult.Done();
            }
        }
    }

    private static String valueToConcatString(Value v) {
        return switch (v.type()) {
            case STRING -> v.asString();
            case CHAR -> String.valueOf(v.charValue());
            case INT -> Integer.toString(v.intValue());
            case LONG -> Long.toString(v.longValue());
            case FLOAT -> Float.toString(v.floatValue());
            case DOUBLE -> Double.toString(v.doubleValue());
            default -> throw new ObrException(
                    RuntimeExecutor.class.getSimpleName() + ": string 拼接不支持类型: " + v.type());
        };
    }

    private void executeVarDecl(Stmt.VarDecl vd, Env env, StaticStore statics, String calleeFile) {
        boolean staticDecl = vd.visibility() != VarVisibility.LOCAL;
        String ty = vd.type().keywordLexeme();
        for (VarDeclarator d : vd.declarators()) {
            if (staticDecl) {
                if (statics.values.containsKey(d.name())) {
                    throw new ObrException(
                            E_RT_STATIC_LOCAL_REDECL
                                    + " 函数内 static 变量重复声明（同名槽已存在）: "
                                    + d.name());
                }
                Value v =
                        d.initOrNull() != null
                                ? widenReturnValue(ty, evalExpr(d.initOrNull(), env, statics, calleeFile))
                                : defaultUninitializedValue(ty);
                statics.values.put(d.name(), v);
                statics.types.put(d.name(), ty);
            } else {
                Value v =
                        d.initOrNull() != null
                                ? widenReturnValue(ty, evalExpr(d.initOrNull(), env, statics, calleeFile))
                                : defaultUninitializedValue(ty);
                env.putLocal(d.name(), ty, v);
            }
        }
    }

    private String resolveDeclaredType(String name, Env env, StaticStore statics, String calleeFile) {
        String t = env.getDeclaredType(name, statics);
        if (t != null) {
            return t;
        }
        List<FileStaticRegistry.Slot> slots = fileStaticSlotsByName.get(name);
        if (slots == null || slots.isEmpty()) {
            return null;
        }
        List<FileStaticRegistry.Slot> visible = visibleFileStaticSlots(slots, calleeFile);
        if (visible.isEmpty()) {
            return null;
        }
        String tk = visible.getFirst().typeKeyword();
        for (FileStaticRegistry.Slot s : visible) {
            if (!s.typeKeyword().equals(tk)) {
                throw new ObrException(
                        E_RT_STATIC_FILE_DUP + " 运行时 file static 同名类型不一致: " + name);
            }
        }
        return tk;
    }

    /**
     * 跨 {@code .obr} 仅 {@link VarVisibility#PUBLIC_STATIC}；同文件内可解析 {@link VarVisibility#PRIVATE_STATIC}。
     */
    private static List<FileStaticRegistry.Slot> visibleFileStaticSlots(
            List<FileStaticRegistry.Slot> slots, String calleeFile) {
        if (slots == null || slots.isEmpty()) {
            return List.of();
        }
        if (calleeFile == null
                || calleeFile.isEmpty()
                || "<unknown>".equals(calleeFile)
                || calleeFile.contains("(native)")) {
            return slots.stream()
                    .filter(s -> s.visibility() == VarVisibility.PUBLIC_STATIC)
                    .toList();
        }
        Path cur;
        try {
            cur = Path.of(calleeFile).toAbsolutePath().normalize();
        } catch (Exception e) {
            return slots.stream()
                    .filter(s -> s.visibility() == VarVisibility.PUBLIC_STATIC)
                    .toList();
        }
        List<FileStaticRegistry.Slot> out = new ArrayList<>();
        for (FileStaticRegistry.Slot s : slots) {
            try {
                Path slotPath = s.sourceObrPath().toAbsolutePath().normalize();
                if (slotPath.equals(cur) || s.visibility() == VarVisibility.PUBLIC_STATIC) {
                    out.add(s);
                }
            } catch (Exception ex) {
                if (s.visibility() == VarVisibility.PUBLIC_STATIC) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    /**
     * 局部/当前函数 static 优先；否则按文件登记解析它函数 static：仅当<strong>恰好一处</strong>已激活时有确定值，
     * 均未激活为 {@link Value#ofUndefined()}，多处已激活则报错。
     */
    private Value getValueForName(String name, Env env, StaticStore statics, String calleeFile) {
        Value v = env.getValue(name, statics);
        if (v != null) {
            return v;
        }
        List<FileStaticRegistry.Slot> slots = fileStaticSlotsByName.get(name);
        if (slots == null || slots.isEmpty()) {
            return null;
        }
        List<FileStaticRegistry.Slot> visible = visibleFileStaticSlots(slots, calleeFile);
        if (visible.isEmpty()) {
            return null;
        }
        List<FunctionSignature> activeOwners = new ArrayList<>();
        for (FileStaticRegistry.Slot s : visible) {
            StaticStore st = functionStaticStores.get(s.owner());
            if (st != null && st.values.containsKey(name)) {
                activeOwners.add(s.owner());
            }
        }
        if (activeOwners.isEmpty()) {
            return Value.ofUndefined();
        }
        if (activeOwners.size() > 1) {
            throw new ObrException(
                    E_RT_STATIC_FILE_DUP + " 运行时 file static 同名已多处激活: " + name);
        }
        return functionStaticStores.get(activeOwners.getFirst()).values.get(name);
    }

    /** 复合赋值与 ++/-- 读当前值：须已绑定且为数值、非 {@link ValueType#UNDEFINED}。 */
    private Value requireNumericForCompound(String name, Env env, StaticStore statics, String calleeFile) {
        Value v = getValueForName(name, env, statics, calleeFile);
        if (v == null) {
            throw new ObrException(E_RT_NAME_UNKNOWN + " 读变量失败: " + name);
        }
        if (v.type() == ValueType.UNDEFINED) {
            throw new ObrException(E_RT_COMPOUND_UNINIT + " 复合赋值或 ++/-- 时变量未初始化: " + name);
        }
        if (!isNumeric(v)) {
            throw new ObrException(E_RT_EXPR_UNSUPPORTED + " 复合赋值或 ++/-- 须为数值类型: " + name);
        }
        return v;
    }

    private static Value literalOneMatching(Value cur) {
        return switch (cur.type()) {
            case INT -> Value.ofInt(1);
            case LONG -> Value.ofLong(1L);
            case FLOAT -> Value.ofFloat(1.0f);
            case DOUBLE -> Value.ofDouble(1.0);
            default -> throw new ObrException(
                    RuntimeExecutor.class.getSimpleName() + ": literalOneMatching 非数值");
        };
    }

    private void assignWithForeign(String name, Value v, Env env, StaticStore statics, String calleeFile) {
        if (env.tryAssign(name, v, statics)) {
            return;
        }
        List<FileStaticRegistry.Slot> slots = fileStaticSlotsByName.get(name);
        if (slots == null || slots.isEmpty()) {
            throw new ObrException(E_RT_NAME_UNKNOWN + " 赋值时未知变量: " + name);
        }
        List<FileStaticRegistry.Slot> visible = visibleFileStaticSlots(slots, calleeFile);
        if (visible.isEmpty()) {
            throw new ObrException(E_RT_NAME_UNKNOWN + " 赋值时未知变量: " + name);
        }
        String tk = visible.getFirst().typeKeyword();
        for (FileStaticRegistry.Slot s : visible) {
            if (!s.typeKeyword().equals(tk)) {
                throw new ObrException(
                        E_RT_STATIC_FILE_DUP + " 运行时 file static 同名类型不一致: " + name);
            }
        }
        List<FunctionSignature> activeOwners = new ArrayList<>();
        for (FileStaticRegistry.Slot s : visible) {
            StaticStore st = functionStaticStores.get(s.owner());
            if (st != null && st.values.containsKey(name)) {
                activeOwners.add(s.owner());
            }
        }
        if (activeOwners.size() > 1) {
            throw new ObrException(
                    E_RT_STATIC_FILE_DUP + " 运行时 file static 同名已多处激活，无法赋值: " + name);
        }
        if (activeOwners.size() == 1) {
            functionStaticStores.get(activeOwners.getFirst()).values.put(name, v);
            return;
        }
        if (visible.size() == 1) {
            FileStaticRegistry.Slot slot = visible.getFirst();
            StaticStore s = functionStaticStores.computeIfAbsent(slot.owner(), k -> new StaticStore());
            s.types.putIfAbsent(name, slot.typeKeyword());
            s.values.put(name, v);
            return;
        }
        throw new ObrException(
                E_RT_STATIC_FILE_DUP
                        + " 赋值时无法确定 file static 所有者（多个候选且尚未激活）: "
                        + name);
    }

    /** 未显式初始化：基础类型为 {@code undefined}；复合类型（后续）为 {@code null}。 */
    private static Value defaultUninitializedValue(String typeKeyword) {
        return Value.ofUndefined();
    }

    private static boolean isPrimitiveTypeKeyword(String t) {
        return switch (t) {
            case "byte", "short", "int", "long", "float", "double", "char", "boolean", "string" -> true;
            default -> false;
        };
    }

    /**
     * 将返回值加宽到函数头声明类型（与 {@link NumericWidening#oneArgCost} 一致）。
     */
    private static Value widenReturnValue(String declaredKeyword, Value v) {
        String a = v.type().keyword;
        if ("undefined".equals(a) && isPrimitiveTypeKeyword(declaredKeyword)) {
            return Value.ofUndefined();
        }
        if (a.equals(declaredKeyword)) {
            return v;
        }
        if (NumericWidening.oneArgCost(a, declaredKeyword) < 0) {
            throw new ObrException(
                    E_RT_RETURN_COERCE + " 运行时无法将返回值 " + a + " 加宽为 " + declaredKeyword);
        }
        return switch (a) {
            case "int" -> switch (declaredKeyword) {
                case "long" -> Value.ofLong(v.intValue());
                case "float" -> Value.ofFloat(v.intValue());
                case "double" -> Value.ofDouble(v.intValue());
                default -> throw new AssertionError(declaredKeyword);
            };
            case "long" -> switch (declaredKeyword) {
                case "float" -> Value.ofFloat(v.longValue());
                case "double" -> Value.ofDouble(v.longValue());
                default -> throw new AssertionError(declaredKeyword);
            };
            case "float" -> Value.ofDouble(v.floatValue());
            default -> throw new AssertionError(a);
        };
    }

    private FunctionSignature resolveRuntimeSignature(FunctionSignature requested) {
        if (defs.containsKey(requested)) {
            return requested;
        }
        int best = Integer.MAX_VALUE;
        FunctionSignature bestSig = null;
        List<FunctionSignature> tied = new java.util.ArrayList<>();
        for (FunctionSignature cand : defs.keySet()) {
            if (!cand.qualifiedName().equals(requested.qualifiedName())
                    || cand.paramTypes().size() != requested.paramTypes().size()) {
                continue;
            }
            int score = NumericWidening.totalWideningCost(requested.paramTypes(), cand.paramTypes());
            if (score < 0) {
                continue;
            }
            if (score < best) {
                best = score;
                bestSig = cand;
                tied.clear();
                tied.add(cand);
            } else if (score == best) {
                tied.add(cand);
            }
        }
        if (bestSig == null) {
            return requested;
        }
        if (tied.size() > 1) {
            StringBuilder sb = new StringBuilder("运行时重载二义性: ").append(requested).append("，候选实现:");
            for (FunctionSignature c : tied) {
                sb.append("\n  ").append(c).append(" @ ").append(defOrigins.getOrDefault(c, "<unknown>"));
            }
            throw new ObrException(E_RT_OVERLOAD_AMBIGUOUS + " " + sb);
        }
        return bestSig;
    }

    private void executeCall(CallExpr call, Env env, StaticStore statics, String callerFile) {
        String qn = String.join("::", call.callee().segments());
        List<Value> args =
                call.arguments().stream().map(a -> evalExpr(a, env, statics, callerFile)).toList();
        call(qn, args, callerFile);
    }

    private Value evalExpr(Expr e, Env env, StaticStore statics, String callerFile) {
        if (e instanceof Expr.Literal lit) {
            String lex = lit.lexeme();
            if (lex.length() >= 3 && lex.startsWith("'") && lex.endsWith("'")) {
                return Value.ofChar(CharLiteralParser.parseCharLexeme(lex));
            }
            if (lex.startsWith("\"") && lex.endsWith("\"") && lex.length() >= 2) {
                return Value.ofString(lex.substring(1, lex.length() - 1));
            }
            if (lex.matches("^[0-9]+[lL]$")) {
                return Value.ofLong(Long.parseLong(lex.substring(0, lex.length() - 1)));
            }
            if (lex.matches("^[0-9]+\\.[0-9]+[fF]$")) {
                return Value.ofFloat(Float.parseFloat(lex.substring(0, lex.length() - 1)));
            }
            if (lex.matches("^[0-9]+\\.[0-9]+([dD])?$")) {
                String n = (lex.endsWith("d") || lex.endsWith("D")) ? lex.substring(0, lex.length() - 1) : lex;
                return Value.ofDouble(Double.parseDouble(n));
            }
            if (lex.matches("^[0-9]+$")) {
                return Value.ofInt(Integer.parseInt(lex));
            }
            if ("true".equals(lex) || "false".equals(lex)) {
                return Value.ofBoolean(Boolean.parseBoolean(lex));
            }
            if ("null".equals(lex)) {
                return Value.ofNull();
            }
            if ("undefined".equals(lex)) {
                return Value.ofUndefined();
            }
            throw new ObrException(E_RT_LITERAL_UNSUPPORTED + " 运行时不支持的字面量: " + lex);
        }
        if (e instanceof Expr.NameRef n) {
            Value v = getValueForName(n.name(), env, statics, callerFile);
            if (v == null) {
                throw new ObrException(E_RT_NAME_UNKNOWN + " 运行时未知变量: " + n.name());
            }
            return v;
        }
        if (e instanceof Expr.Invoke inv) {
            String qn = String.join("::", inv.call().callee().segments());
            List<Value> args =
                    inv.call().arguments().stream().map(a -> evalExpr(a, env, statics, callerFile)).toList();
            Value r = call(qn, args, callerFile);
            if (r == null) {
                throw new ObrException(
                        E_RT_EXPR_UNSUPPORTED + " void 函数调用不能作为表达式值: " + qn);
            }
            return r;
        }
        if (e instanceof Expr.Unary u) {
            return evalUnary(u, env, statics, callerFile);
        }
        if (e instanceof Expr.Binary b) {
            return evalBinary(b, env, statics, callerFile);
        }
        if (e instanceof Expr.Conditional cond) {
            Value c = evalExpr(cond.cond(), env, statics, callerFile);
            if (truthy(c)) {
                return evalExpr(cond.thenExpr(), env, statics, callerFile);
            }
            return evalExpr(cond.elseExpr(), env, statics, callerFile);
        }
        if (e instanceof Expr.PrefixUpdate p) {
            if (!(p.operand() instanceof Expr.NameRef n)) {
                throw new ObrException(E_RT_EXPR_UNSUPPORTED + " 前缀 ++/-- 仅适用于变量");
            }
            String ty = resolveDeclaredType(n.name(), env, statics, callerFile);
            if (ty == null) {
                throw new ObrException(E_RT_NAME_UNKNOWN + " 前缀 ++/-- 时未知变量: " + n.name());
            }
            Value cur = requireNumericForCompound(n.name(), env, statics, callerFile);
            Value one = literalOneMatching(cur);
            boolean incr = p.op() == Expr.PostfixOp.INCR;
            Value next = incr ? evalArithmetic(cur, one, 0) : evalArithmetic(cur, one, 1);
            assignWithForeign(n.name(), widenReturnValue(ty, next), env, statics, callerFile);
            return next;
        }
        if (e instanceof Expr.Postfix p) {
            if (!(p.operand() instanceof Expr.NameRef n)) {
                throw new ObrException(E_RT_EXPR_UNSUPPORTED + " ++/-- 仅适用于变量");
            }
            String ty = resolveDeclaredType(n.name(), env, statics, callerFile);
            if (ty == null) {
                throw new ObrException(E_RT_NAME_UNKNOWN + " ++/-- 时未知变量: " + n.name());
            }
            Value cur = requireNumericForCompound(n.name(), env, statics, callerFile);
            Value one = literalOneMatching(cur);
            boolean incr = p.op() == Expr.PostfixOp.INCR;
            Value next = incr ? evalArithmetic(cur, one, 0) : evalArithmetic(cur, one, 1);
            assignWithForeign(n.name(), widenReturnValue(ty, next), env, statics, callerFile);
            return cur;
        }
        throw new ObrException(E_RT_EXPR_UNSUPPORTED + " 运行时不支持的表达式");
    }

    private Value evalBinary(Expr.Binary b, Env env, StaticStore statics, String callerFile) {
        return switch (b.op()) {
            case AND -> {
                Value left = evalExpr(b.left(), env, statics, callerFile);
                if (!truthy(left)) {
                    yield Value.ofBoolean(false);
                }
                yield Value.ofBoolean(truthy(evalExpr(b.right(), env, statics, callerFile)));
            }
            case OR -> {
                Value left = evalExpr(b.left(), env, statics, callerFile);
                if (truthy(left)) {
                    yield Value.ofBoolean(true);
                }
                yield Value.ofBoolean(truthy(evalExpr(b.right(), env, statics, callerFile)));
            }
            case EQ, NE -> {
                Value left = evalExpr(b.left(), env, statics, callerFile);
                Value right = evalExpr(b.right(), env, statics, callerFile);
                boolean eq = valuesEqual(left, right);
                yield Value.ofBoolean(b.op() == Expr.BinaryOp.EQ ? eq : !eq);
            }
            case LT, LE, GT, GE -> {
                Value left = evalExpr(b.left(), env, statics, callerFile);
                Value right = evalExpr(b.right(), env, statics, callerFile);
                yield Value.ofBoolean(compareRelationalIntegers(left, right, b.op()));
            }
            case ADD -> evalAdd(b, env, statics, callerFile);
            case SUB, MUL, DIV, MOD -> evalBinaryNumeric(b, env, statics, callerFile);
            case POW -> evalPowExpr(b, env, statics, callerFile);
        };
    }

    private static boolean valuesEqual(Value left, Value right) {
        if (left.type() != right.type()) {
            return false;
        }
        return switch (left.type()) {
            case STRING -> left.asString() == right.asString();
            case CHAR -> left.charValue() == right.charValue();
            case INT -> left.intValue() == right.intValue();
            case LONG -> left.longValue() == right.longValue();
            case FLOAT -> Float.compare(left.floatValue(), right.floatValue()) == 0;
            case DOUBLE -> Double.compare(left.doubleValue(), right.doubleValue()) == 0;
            default -> throw new ObrException(
                    RuntimeExecutor.class.getSimpleName() + ": == 不支持类型: " + left.type());
        };
    }

    private static boolean compareRelationalIntegers(Value left, Value right, Expr.BinaryOp op) {
        long a = toLongInteger(left);
        long b = toLongInteger(right);
        return switch (op) {
            case LT -> a < b;
            case LE -> a <= b;
            case GT -> a > b;
            case GE -> a >= b;
            default -> throw new AssertionError();
        };
    }

    private static long toLongInteger(Value v) {
        return switch (v.type()) {
            case INT -> v.intValue();
            case LONG -> v.longValue();
            default -> throw new ObrException(
                    RuntimeExecutor.class.getSimpleName() + ": 关系运算期望整数标量: " + v.type());
        };
    }

    private Value evalAdd(Expr.Binary b, Env env, StaticStore statics, String callerFile) {
        Value left = evalExpr(b.left(), env, statics, callerFile);
        Value right = evalExpr(b.right(), env, statics, callerFile);
        if (left.type() == ValueType.STRING
                || right.type() == ValueType.STRING
                || left.type() == ValueType.CHAR
                || right.type() == ValueType.CHAR) {
            return Value.ofString(valueToConcatString(left) + valueToConcatString(right));
        }
        if (b.left() instanceof Expr.NameRef nl
                && b.right() instanceof Expr.NameRef nr
                && "byte".equals(resolveDeclaredType(nl.name(), env, statics, callerFile))
                && "byte".equals(resolveDeclaredType(nr.name(), env, statics, callerFile))) {
            int a = left.intValue() & 1;
            int c = right.intValue() & 1;
            return Value.ofInt((a + c) % 2);
        }
        return evalPromotedArithmetic(left, right, 0);
    }

    private Value evalPowExpr(Expr.Binary b, Env env, StaticStore statics, String callerFile) {
        Value left = evalExpr(b.left(), env, statics, callerFile);
        Value right = evalExpr(b.right(), env, statics, callerFile);
        if (b.left() instanceof Expr.NameRef nl
                && b.right() instanceof Expr.NameRef nr
                && "byte".equals(resolveDeclaredType(nl.name(), env, statics, callerFile))
                && "byte".equals(resolveDeclaredType(nr.name(), env, statics, callerFile))) {
            double p = Math.pow(left.intValue() & 1, right.intValue() & 1);
            return Value.ofInt(((int) p) % 2);
        }
        return evalPowNumeric(left, right);
    }

    private Value evalBinaryNumeric(Expr.Binary b, Env env, StaticStore statics, String callerFile) {
        Value left = evalExpr(b.left(), env, statics, callerFile);
        Value right = evalExpr(b.right(), env, statics, callerFile);
        if (isByteVarPair(b.left(), b.right(), env, statics, callerFile)) {
            return evalByteBinary(left, right, b.op());
        }
        return switch (b.op()) {
            case SUB -> evalPromotedArithmetic(left, right, 1);
            case MUL -> evalPromotedArithmetic(left, right, 2);
            case DIV -> evalPromotedDiv(left, right);
            case MOD -> evalPromotedMod(left, right);
            default -> throw new AssertionError();
        };
    }

    private boolean isByteVarPair(Expr l, Expr r, Env env, StaticStore statics, String calleeFile) {
        if (l instanceof Expr.NameRef nl && r instanceof Expr.NameRef nr) {
            return "byte".equals(resolveDeclaredType(nl.name(), env, statics, calleeFile))
                    && "byte".equals(resolveDeclaredType(nr.name(), env, statics, calleeFile));
        }
        return false;
    }

    private Value evalByteBinary(Value left, Value right, Expr.BinaryOp op) {
        int a = left.intValue() & 1;
        int c = right.intValue() & 1;
        return switch (op) {
            case SUB -> Value.ofInt((a - c + 2) % 2);
            case MUL -> Value.ofInt((a * c) % 2);
            case DIV -> {
                if (c == 0) {
                    throw new ObrException(E_RT_INTEGER_DIV_ZERO + " byte 除零");
                }
                yield Value.ofInt((a / c) % 2);
            }
            case MOD -> {
                if (c == 0) {
                    throw new ObrException(E_RT_INTEGER_DIV_ZERO + " byte 取模除零");
                }
                yield Value.ofInt((a % c) % 2);
            }
            default -> throw new AssertionError();
        };
    }

    private Value evalPromotedArithmetic(Value left, Value right, int mode) {
        String k1 = left.type().keyword;
        String k2 = right.type().keyword;
        try {
            if (NumericExprTyping.illegalByteMix(k1, k2)) {
                throw new ObrException(E_RT_EXPR_UNSUPPORTED + " byte 与其它数值混用");
            }
            String out = NumericExprTyping.promotedArithmeticType(k1, k2);
            if ("double".equals(out)) {
                double d1 = valueToDouble(left);
                double d2 = valueToDouble(right);
                double dr =
                        switch (mode) {
                            case 0 -> d1 + d2;
                            case 1 -> d1 - d2;
                            case 2 -> d1 * d2;
                            default -> throw new AssertionError();
                        };
                return Value.ofDouble(dr);
            }
            if ("float".equals(out)) {
                float d1 = valueToFloat(left);
                float d2 = valueToFloat(right);
                float dr =
                        switch (mode) {
                            case 0 -> d1 + d2;
                            case 1 -> d1 - d2;
                            case 2 -> d1 * d2;
                            default -> throw new AssertionError();
                        };
                return Value.ofFloat(dr);
            }
            if ("long".equals(out)) {
                long a = valueToLong(left);
                long c = valueToLong(right);
                long r =
                        switch (mode) {
                            case 0 -> a + c;
                            case 1 -> a - c;
                            case 2 -> a * c;
                            default -> throw new AssertionError();
                        };
                return Value.ofLong(r);
            }
            int a = left.intValue();
            int c = right.intValue();
            int r =
                    switch (mode) {
                        case 0 -> a + c;
                        case 1 -> a - c;
                        case 2 -> a * c;
                        default -> throw new AssertionError();
                    };
            return Value.ofInt(r);
        } catch (IllegalArgumentException e) {
            throw new ObrException(E_RT_EXPR_UNSUPPORTED + " 数值运算: " + e.getMessage());
        }
    }

    private Value evalPromotedDiv(Value left, Value right) {
        String k1 = left.type().keyword;
        String k2 = right.type().keyword;
        if (NumericExprTyping.illegalByteMix(k1, k2)) {
            throw new ObrException(E_RT_EXPR_UNSUPPORTED + " byte 与其它数值混用");
        }
        String out = NumericExprTyping.promotedArithmeticType(k1, k2);
        if ("double".equals(out)) {
            double d1 = valueToDouble(left);
            double d2 = valueToDouble(right);
            return Value.ofDouble(d1 / d2);
        }
        if ("float".equals(out)) {
            float d1 = valueToFloat(left);
            float d2 = valueToFloat(right);
            return Value.ofFloat(d1 / d2);
        }
        if ("long".equals(out)) {
            long a = valueToLong(left);
            long c = valueToLong(right);
            if (c == 0L) {
                throw new ObrException(E_RT_INTEGER_DIV_ZERO + " 整型除零");
            }
            return Value.ofLong(a / c);
        }
        int a = left.intValue();
        int c = right.intValue();
        if (c == 0) {
            throw new ObrException(E_RT_INTEGER_DIV_ZERO + " 整型除零");
        }
        return Value.ofInt(a / c);
    }

    private Value evalPromotedMod(Value left, Value right) {
        String k1 = left.type().keyword;
        String k2 = right.type().keyword;
        if (NumericExprTyping.illegalByteMix(k1, k2)) {
            throw new ObrException(E_RT_EXPR_UNSUPPORTED + " byte 与其它数值混用");
        }
        String out = NumericExprTyping.promotedArithmeticType(k1, k2);
        if ("double".equals(out)) {
            double d1 = valueToDouble(left);
            double d2 = valueToDouble(right);
            return Value.ofDouble(d1 % d2);
        }
        if ("float".equals(out)) {
            float d1 = valueToFloat(left);
            float d2 = valueToFloat(right);
            return Value.ofFloat(d1 % d2);
        }
        if ("long".equals(out)) {
            long a = valueToLong(left);
            long c = valueToLong(right);
            if (c == 0L) {
                throw new ObrException(E_RT_INTEGER_DIV_ZERO + " 整型取模除零");
            }
            return Value.ofLong(a % c);
        }
        int a = left.intValue();
        int c = right.intValue();
        if (c == 0) {
            throw new ObrException(E_RT_INTEGER_DIV_ZERO + " 整型取模除零");
        }
        return Value.ofInt(a % c);
    }

    private static long valueToLong(Value v) {
        return switch (v.type()) {
            case INT -> v.intValue();
            case LONG -> v.longValue();
            default -> throw new ObrException(RuntimeExecutor.class.getSimpleName() + ": 期望 int/long");
        };
    }

    private static float valueToFloat(Value v) {
        return switch (v.type()) {
            case INT -> v.intValue();
            case LONG -> v.longValue();
            case FLOAT -> v.floatValue();
            case DOUBLE -> (float) v.doubleValue();
            default -> throw new ObrException(RuntimeExecutor.class.getSimpleName() + ": 期望数值");
        };
    }

    /** mode: 0 +, 1 -, 2 * */
    private Value evalArithmetic(Value left, Value right, int mode) {
        if (left.type() != right.type()) {
            throw new ObrException(E_RT_EXPR_UNSUPPORTED + " 算术运算两侧类型不一致");
        }
        return switch (left.type()) {
            case INT -> {
                int a = left.intValue();
                int b = right.intValue();
                yield switch (mode) {
                    case 0 -> Value.ofInt(a + b);
                    case 1 -> Value.ofInt(a - b);
                    case 2 -> Value.ofInt(a * b);
                    default -> throw new AssertionError();
                };
            }
            case LONG -> {
                long a = left.longValue();
                long b = right.longValue();
                yield switch (mode) {
                    case 0 -> Value.ofLong(a + b);
                    case 1 -> Value.ofLong(a - b);
                    case 2 -> Value.ofLong(a * b);
                    default -> throw new AssertionError();
                };
            }
            case FLOAT -> {
                float a = left.floatValue();
                float b = right.floatValue();
                yield switch (mode) {
                    case 0 -> Value.ofFloat(a + b);
                    case 1 -> Value.ofFloat(a - b);
                    case 2 -> Value.ofFloat(a * b);
                    default -> throw new AssertionError();
                };
            }
            case DOUBLE -> {
                double a = left.doubleValue();
                double b = right.doubleValue();
                yield switch (mode) {
                    case 0 -> Value.ofDouble(a + b);
                    case 1 -> Value.ofDouble(a - b);
                    case 2 -> Value.ofDouble(a * b);
                    default -> throw new AssertionError();
                };
            }
            default -> throw new ObrException(E_RT_EXPR_UNSUPPORTED + " 算术运算不支持类型: " + left.type());
        };
    }

    private Value evalDiv(Value left, Value right) {
        if (left.type() != right.type()) {
            throw new ObrException(E_RT_EXPR_UNSUPPORTED + " / 两侧类型不一致");
        }
        return switch (left.type()) {
            case INT -> {
                int b = right.intValue();
                if (b == 0) {
                    throw new ObrException(E_RT_INTEGER_DIV_ZERO + " 整型除零");
                }
                yield Value.ofInt(left.intValue() / b);
            }
            case LONG -> {
                long b = right.longValue();
                if (b == 0L) {
                    throw new ObrException(E_RT_INTEGER_DIV_ZERO + " 整型除零");
                }
                yield Value.ofLong(left.longValue() / b);
            }
            case FLOAT -> Value.ofFloat(left.floatValue() / right.floatValue());
            case DOUBLE -> Value.ofDouble(left.doubleValue() / right.doubleValue());
            default -> throw new ObrException(E_RT_EXPR_UNSUPPORTED + " / 不支持类型: " + left.type());
        };
    }

    private Value evalMod(Value left, Value right) {
        if (left.type() != right.type()) {
            throw new ObrException(E_RT_EXPR_UNSUPPORTED + " % 两侧类型不一致");
        }
        return switch (left.type()) {
            case INT -> {
                int b = right.intValue();
                if (b == 0) {
                    throw new ObrException(E_RT_INTEGER_DIV_ZERO + " 整型取模除零");
                }
                yield Value.ofInt(left.intValue() % b);
            }
            case LONG -> {
                long b = right.longValue();
                if (b == 0L) {
                    throw new ObrException(E_RT_INTEGER_DIV_ZERO + " 整型取模除零");
                }
                yield Value.ofLong(left.longValue() % b);
            }
            case FLOAT -> Value.ofFloat(left.floatValue() % right.floatValue());
            case DOUBLE -> Value.ofDouble(left.doubleValue() % right.doubleValue());
            default -> throw new ObrException(E_RT_EXPR_UNSUPPORTED + " % 不支持类型: " + left.type());
        };
    }

    private Value evalPowNumeric(Value left, Value right) {
        double a = valueToDouble(left);
        double b = valueToDouble(right);
        return Value.ofDouble(Math.pow(a, b));
    }

    private static double valueToDouble(Value v) {
        return switch (v.type()) {
            case INT -> v.intValue();
            case LONG -> v.longValue();
            case FLOAT -> v.floatValue();
            case DOUBLE -> v.doubleValue();
            default -> throw new ObrException(E_RT_EXPR_UNSUPPORTED + " ** 操作数须为数值");
        };
    }

    private Value evalUnary(Expr.Unary u, Env env, StaticStore statics, String callerFile) {
        Value v = evalExpr(u.operand(), env, statics, callerFile);
        return switch (u.op()) {
            case POS -> evalUnaryPos(v);
            case NEG -> evalUnaryNeg(v);
            case LNOT -> Value.ofBoolean(!truthy(v));
            case BITNOT -> evalUnaryBitNot(v);
        };
    }

    private static Value evalUnaryPos(Value v) {
        if (!isNumeric(v)) {
            throw new ObrException(E_RT_EXPR_UNSUPPORTED + " 一元 + 须为数值");
        }
        return v;
    }

    private static Value evalUnaryNeg(Value v) {
        return switch (v.type()) {
            case INT -> Value.ofInt(-v.intValue());
            case LONG -> Value.ofLong(-v.longValue());
            case FLOAT -> Value.ofFloat(-v.floatValue());
            case DOUBLE -> Value.ofDouble(-v.doubleValue());
            default -> throw new ObrException(E_RT_EXPR_UNSUPPORTED + " 一元 - 须为数值");
        };
    }

    private static Value evalUnaryBitNot(Value v) {
        int bits =
                v.type() == ValueType.LONG ? (int) v.longValue() : v.intValue();
        return Value.ofInt(~bits);
    }

    private static boolean isNumeric(Value v) {
        return v.type() == ValueType.INT
                || v.type() == ValueType.LONG
                || v.type() == ValueType.FLOAT
                || v.type() == ValueType.DOUBLE;
    }

    private static boolean truthy(Value v) {
        return switch (v.type()) {
            case BOOLEAN -> v.booleanValue();
            case INT -> v.intValue() != 0;
            case LONG -> v.longValue() != 0L;
            case FLOAT -> v.floatValue() != 0.0f;
            case DOUBLE -> v.doubleValue() != 0.0;
            case STRING -> !v.asString().isEmpty();
            case CHAR -> v.charValue() != 0;
            case NULL_VALUE, UNDEFINED -> false;
        };
    }

    private String formatStackWith(String next) {
        java.util.LinkedList<String> chain = new java.util.LinkedList<>();
        for (Frame f : stack) {
            chain.addFirst(f.qn());
        }
        chain.add(next);
        return String.join(" -> ", chain);
    }

    private record Frame(String qn, String file) {}

    private enum ValueType {
        STRING("string"),
        CHAR("char"),
        INT("int"),
        LONG("long"),
        FLOAT("float"),
        DOUBLE("double"),
        BOOLEAN("boolean"),
        NULL_VALUE("null"),
        UNDEFINED("undefined");

        private final String keyword;

        ValueType(String keyword) {
            this.keyword = keyword;
        }
    }

    private record Value(ValueType type, Object value) {
        static Value ofString(String s) {
            return new Value(ValueType.STRING, s);
        }

        static Value ofChar(char ch) {
            return new Value(ValueType.CHAR, ch);
        }

        static Value ofInt(int n) {
            return new Value(ValueType.INT, n);
        }

        static Value ofLong(long n) {
            return new Value(ValueType.LONG, n);
        }

        static Value ofFloat(float n) {
            return new Value(ValueType.FLOAT, n);
        }

        static Value ofDouble(double n) {
            return new Value(ValueType.DOUBLE, n);
        }

        static Value ofBoolean(boolean b) {
            return new Value(ValueType.BOOLEAN, b);
        }

        static Value ofNull() {
            return new Value(ValueType.NULL_VALUE, null);
        }

        static Value ofUndefined() {
            return new Value(ValueType.UNDEFINED, null);
        }

        String asString() {
            return (String) value;
        }

        boolean booleanValue() {
            return (Boolean) value;
        }

        char charValue() {
            return (Character) value;
        }

        int intValue() {
            return (Integer) value;
        }

        long longValue() {
            return (Long) value;
        }

        float floatValue() {
            return (Float) value;
        }

        double doubleValue() {
            return (Double) value;
        }
    }
}
