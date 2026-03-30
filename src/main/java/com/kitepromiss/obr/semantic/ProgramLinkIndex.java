package com.kitepromiss.obr.semantic;

import com.kitepromiss.obr.ast.ObrItem;
import com.kitepromiss.obr.module.ObrProgramBundle;
import com.kitepromiss.obr.project.LinkAccess;
import com.kitepromiss.obr.project.LinkParser;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 程序内各 {@code .obr} 的 {@code #LINK} 列表，以及 {@code deRfun} 定义所在文件（用于跨文件访问判定）。
 */
public final class ProgramLinkIndex {

    private final Path projectRoot;
    private final Map<Path, List<String>> linkItemsByFile;
    private final Map<FunctionSignature, Path> defFileBySig;

    private ProgramLinkIndex(
            Path projectRoot,
            Map<Path, List<String>> linkItemsByFile,
            Map<FunctionSignature, Path> defFileBySig) {
        this.projectRoot = projectRoot;
        this.linkItemsByFile = linkItemsByFile;
        this.defFileBySig = defFileBySig;
    }

    public static ProgramLinkIndex from(ObrProgramBundle program, Path projectRoot) {
        Path root = projectRoot.toAbsolutePath().normalize();
        LinkedHashMap<FunctionSignature, Path> defs = new LinkedHashMap<>();
        for (ObrProgramBundle.ParsedObrFile f : program.files()) {
            for (ObrItem item : f.ast().items()) {
                if (item instanceof ObrItem.DeRfunDef def) {
                    FunctionSignature sig = FunctionSignature.of(def.name(), def.params());
                    defs.putIfAbsent(sig, keyPath(f.path()));
                }
            }
        }
        Map<Path, List<String>> links = new HashMap<>();
        for (ObrProgramBundle.ParsedObrFile f : program.files()) {
            List<String> items = LinkParser.parseMergedLinkItemsFromAst(f.ast());
            if (items.isEmpty()) {
                items = List.of("/");
            }
            links.put(keyPath(f.path()), List.copyOf(items));
        }
        return new ProgramLinkIndex(root, Map.copyOf(links), Map.copyOf(defs));
    }

    private static Path keyPath(Path p) {
        return p.toAbsolutePath().normalize();
    }

    /** 解释器托管的 {@code libs/} 下实现不参与 #LINK 访问限制（与 {@code system.md} 托管资源一致）。 */
    public boolean isLibsSystemPath(Path obrPath) {
        Path abs = obrPath.toAbsolutePath().normalize();
        Path libs = projectRoot.resolve("libs").toAbsolutePath().normalize();
        return abs.startsWith(libs);
    }

    public Path defPathFor(FunctionSignature sig) {
        return defFileBySig.get(sig);
    }

    /**
     * 访问方 {@code callerPath} 是否允许使用定义于 {@code defPath} 的符号（同文件恒为真；{@link #isLibsSystemPath(Path)} 恒为真）。
     */
    public boolean allowsAccess(Path callerPath, Path defPath) {
        Path absC = callerPath.toAbsolutePath().normalize();
        Path absD = defPath.toAbsolutePath().normalize();
        if (absC.equals(absD)) {
            return true;
        }
        if (isLibsSystemPath(absD)) {
            return true;
        }
        String callerRel = normalizedRelPath(projectRoot, callerPath);
        List<String> items = linkItemsByFile.getOrDefault(absD, List.of("/"));
        for (String it : items) {
            if (LinkAccess.hits(callerRel, it)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizedRelPath(Path projectRoot, Path file) {
        Path absRoot = projectRoot.toAbsolutePath().normalize();
        Path absFile = file.toAbsolutePath().normalize();
        Path rel = absRoot.relativize(absFile);
        return "/" + rel.toString().replace('\\', '/');
    }
}
