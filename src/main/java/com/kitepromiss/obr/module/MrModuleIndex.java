package com.kitepromiss.obr.module;

import com.kitepromiss.obr.ObrException;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 项目内所有 {@code *.mr} 按模块名（不含扩展名）建立唯一路径映射；同名多路径则报错（moduleR 5.4）。
 */
public final class MrModuleIndex {

    private final Path projectRoot;
    private final Map<String, Path> moduleToPath;

    private MrModuleIndex(Path projectRoot, Map<String, Path> moduleToPath) {
        this.projectRoot = projectRoot;
        this.moduleToPath = moduleToPath;
    }

    public Path projectRoot() {
        return projectRoot;
    }

    public Map<String, Path> asMap() {
        return Map.copyOf(moduleToPath);
    }

    /**
     * @throws ObrException 未找到或存在多条路径对应同一模块名
     */
    public Path require(String moduleName) {
        Path p = moduleToPath.get(moduleName);
        if (p == null) {
            throw new ObrException("找不到模块 " + moduleName + ".mr（已扫描项目根: " + projectRoot + "）");
        }
        return p;
    }

    public static MrModuleIndex scan(Path projectRoot) {
        Objects.requireNonNull(projectRoot, "projectRoot");
        Path root = projectRoot.toAbsolutePath().normalize();
        Map<String, Path> map = new HashMap<>();
        try {
            Files.walkFileTree(
                    root,
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                            String name = dir.getFileName().toString();
                            if (name.equals(".git")
                                    || name.equals("target")
                                    || name.equals("build")
                                    || name.equals(".idea")
                                    || name.equals("node_modules")) {
                                return FileVisitResult.SKIP_SUBTREE;
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                            if (!attrs.isRegularFile()) {
                                return FileVisitResult.CONTINUE;
                            }
                            String fn = file.getFileName().toString();
                            if (!fn.toLowerCase(Locale.ROOT).endsWith(".mr")) {
                                return FileVisitResult.CONTINUE;
                            }
                            String mod = fn.substring(0, fn.length() - 3);
                            Path prev = map.putIfAbsent(mod, file);
                            if (prev != null && !prev.equals(file)) {
                                throw new ObrException(
                                        "模块名冲突："
                                                + mod
                                                + ".mr 对应多条路径:\n  "
                                                + prev
                                                + "\n  "
                                                + file);
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException e) {
            throw new ObrException("扫描 .mr 失败: " + e.getMessage());
        }
        return new MrModuleIndex(root, Map.copyOf(map));
    }
}
