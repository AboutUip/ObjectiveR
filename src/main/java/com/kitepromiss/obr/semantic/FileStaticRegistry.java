package com.kitepromiss.obr.semantic;

import com.kitepromiss.obr.ObrException;
import com.kitepromiss.obr.ast.BlockStmt;
import com.kitepromiss.obr.ast.ObrFile;
import com.kitepromiss.obr.ast.ObrItem;
import com.kitepromiss.obr.ast.ParamDecl;
import com.kitepromiss.obr.ast.Stmt;
import com.kitepromiss.obr.ast.VarDeclarator;
import com.kitepromiss.obr.ast.VarVisibility;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 同一 {@code .obr} 内 {@code static} 变量在全部 {@code deRfun} 之间可见（按名解析，最近块仍优先于文件级
 * static 的语义由块栈处理；此处只登记「某函数拥有的 static」供其它函数体解析标识符）。
 * {@link VarVisibility#PRIVATE_STATIC} 仅与同 {@code .obr} 内其它 {@code deRfun} 共享；跨文件仅
 * {@link VarVisibility#PUBLIC_STATIC} 参与合并解析。
 *
 * <p>同名 static 可分属不同函数；扫描仅登记。多处「激活」冲突在运行时解析裸名时处理（见运行时与语义
 * {@code resolveNameForUse}）。
 */
public final class FileStaticRegistry {

    private static final String E_SEM_VAR_DUP = "E_SEM_VAR_DUP";

    public record Slot(
            String name,
            String typeKeyword,
            FunctionSignature owner,
            VarVisibility visibility,
            Path sourceObrPath) {}

    private FileStaticRegistry() {}

    /** 扫描整文件，收集 static 槽位；同名可对应多个 {@link Slot}（不同 {@code deRfun} 所有者）。 */
    public static Map<String, List<Slot>> collect(ObrFile obr, Path obrPath) {
        Map<String, List<Slot>> file = new HashMap<>();
        for (ObrItem item : obr.items()) {
            if (!(item instanceof ObrItem.DeRfunDef def)) {
                continue;
            }
            FunctionSignature sig = FunctionSignature.of(def.name(), def.params());
            LocalTyScope locals = new LocalTyScope();
            for (ParamDecl p : def.params()) {
                locals.declare(p.name(), p.type().keywordLexeme());
            }
            scanBlock(def.body(), sig, file, obrPath, locals);
        }
        return file;
    }

    /** 仅类型名，用于 StaticMark 解析与局部遮蔽链。 */
    private static final class LocalTyScope {
        private final List<Map<String, String>> frames = new ArrayList<>();

        LocalTyScope() {
            push();
        }

        void push() {
            frames.add(new HashMap<>());
        }

        void pop() {
            frames.removeLast();
        }

        void declare(String name, String ty) {
            frames.getLast().put(name, ty);
        }

        String resolve(String name) {
            for (int i = frames.size() - 1; i >= 0; i--) {
                String t = frames.get(i).get(name);
                if (t != null) {
                    return t;
                }
            }
            return null;
        }
    }

    private static void register(
            Map<String, List<Slot>> file,
            Path obrPath,
            String name,
            String ty,
            FunctionSignature owner,
            VarVisibility visibility) {
        List<Slot> list = file.computeIfAbsent(name, k -> new ArrayList<>());
        for (Slot s : list) {
            if (s.owner().equals(owner)) {
                throw new ObrException(
                        E_SEM_VAR_DUP + " " + obrPath + ": 同函数内 static 同名重复: " + name);
            }
        }
        list.add(new Slot(name, ty, owner, visibility, obrPath));
    }

    private static void scanBlock(
            BlockStmt block,
            FunctionSignature owner,
            Map<String, List<Slot>> file,
            Path obrPath,
            LocalTyScope locals) {
        locals.push();
        try {
            for (Stmt s : block.statements()) {
                scanStmt(s, owner, file, obrPath, locals);
            }
        } finally {
            locals.pop();
        }
    }

    private static void scanStmt(
            Stmt s,
            FunctionSignature owner,
            Map<String, List<Slot>> file,
            Path obrPath,
            LocalTyScope locals) {
        switch (s) {
            case Stmt.VarDecl vd -> {
                String ty = vd.type().keywordLexeme();
                boolean isStaticDecl = vd.visibility() != VarVisibility.LOCAL;
                for (VarDeclarator d : vd.declarators()) {
                    if (isStaticDecl) {
                        register(file, obrPath, d.name(), ty, owner, vd.visibility());
                    }
                    locals.declare(d.name(), ty);
                }
            }
            case Stmt.StaticMark sm -> {
                String ty = locals.resolve(sm.name());
                if (ty == null) {
                    throw new ObrException(
                            "E_SEM_STATIC_MARK_BAD "
                                    + obrPath
                                    + ": static 标记预扫描未找到局部变量: "
                                    + sm.name());
                }
                register(file, obrPath, sm.name(), ty, owner, VarVisibility.PUBLIC_STATIC);
            }
            case Stmt.Block blk -> scanBlock(blk.body(), owner, file, obrPath, locals);
            default -> {
                /* Expression / Return / Assign：无 static 登记 */
            }
        }
    }
}
