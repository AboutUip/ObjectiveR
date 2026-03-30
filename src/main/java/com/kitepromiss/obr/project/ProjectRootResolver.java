package com.kitepromiss.obr.project;

import com.kitepromiss.obr.ObrException;

import java.nio.file.Path;
import java.util.List;

/**
 * {@code docs/obr/runtime.md} §3：由入口 {@code main.obr} 的 {@code #LINK} 与物理路径确定项目根。
 */
public final class ProjectRootResolver {

    private static final String E_LINK_ROOT_MISMATCH = "E_LINK_ROOT_MISMATCH";

    private ProjectRootResolver() {}

    /**
     * @param mainPath 已规范化的 {@code main.obr} 绝对路径
     * @param mainSource 与 {@code mainPath} 对应的源码全文（用于解析 {@code #LINK}）
     */
    public static Path resolveProjectRoot(Path mainPath, String mainSource) {
        List<String> items = LinkParser.parseMergedLinkItemsFromSource(mainSource);
        if (items.isEmpty()) {
            items = List.of("/");
        }
        Path m = mainPath.toAbsolutePath().normalize();
        Path parent = m.getParent();
        if (parent == null) {
            throw new ObrException(E_LINK_ROOT_MISMATCH + " 无法解析 main.obr 父目录: " + m);
        }
        boolean hasMainMain =
                items.stream()
                        .map(LinkParser::normalizePathItem)
                        .anyMatch("/main/main.obr"::equals);
        if (!hasMainMain) {
            return parent;
        }
        Path root = parent.getParent();
        if (root == null) {
            throw new ObrException(
                    E_LINK_ROOT_MISMATCH
                            + " #LINK 含 /main/main.obr 时项目根须为 main.obr 上两级父目录，实际无法得到: "
                            + m);
        }
        Path expected = root.resolve("main").resolve("main.obr").normalize();
        if (!expected.equals(m)) {
            throw new ObrException(
                    E_LINK_ROOT_MISMATCH
                            + " #LINK 声明 /main/main.obr 时，入口文件须位于 <项目根>/main/main.obr，实际为: "
                            + m
                            + " 期望: "
                            + expected);
        }
        return root;
    }
}
