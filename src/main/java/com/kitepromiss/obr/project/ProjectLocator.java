package com.kitepromiss.obr.project;

import com.kitepromiss.obr.ObrException;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * 按执行模型（runtime）：启动参数可为 {@code main.obr} 路径，或包含唯一 {@code main.obr} 的目录。
 */
public final class ProjectLocator {

    private static final String ENTRY_NAME = "main.obr";

    private ProjectLocator() {}

    /**
     * @param input 现有文件路径（须为 {@code main.obr}）或目录路径
     */
    public static ProjectResolution resolve(Path input) {
        if (!Files.exists(input)) {
            throw new ObrException("路径不存在: " + input.toAbsolutePath().normalize());
        }
        try {
            if (Files.isDirectory(input)) {
                return resolveDirectory(input.toAbsolutePath().normalize());
            }
            if (Files.isRegularFile(input)) {
                return resolveFile(input.toAbsolutePath().normalize());
            }
        } catch (IOException e) {
            throw new ObrException("无法访问路径: " + input + " — " + e.getMessage());
        }
        throw new ObrException("不支持的启动路径类型: " + input);
    }

    private static ProjectResolution resolveFile(Path file) throws IOException {
        if (!ENTRY_NAME.equals(file.getFileName().toString())) {
            throw new ObrException("入口文件必须严格命名为 main.obr（大小写敏感），实际为: " + file.getFileName());
        }
        String src = Files.readString(file);
        Path root = ProjectRootResolver.resolveProjectRoot(file, src);
        return new ProjectResolution(file, root);
    }

    private static ProjectResolution resolveDirectory(Path dir) throws IOException {
        List<Path> mains = new ArrayList<>();
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile() && ENTRY_NAME.equals(file.getFileName().toString())) {
                    mains.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        if (mains.isEmpty()) {
            throw new ObrException("在目录中未找到 main.obr: " + dir);
        }
        if (mains.size() > 1) {
            StringBuilder sb = new StringBuilder("项目内存在多个 main.obr（必须唯一）:");
            for (Path p : mains) {
                sb.append("\n  ").append(p);
            }
            throw new ObrException(sb.toString());
        }
        Path main = mains.getFirst();
        String src = Files.readString(main);
        Path root = ProjectRootResolver.resolveProjectRoot(main, src);
        return new ProjectResolution(main, root);
    }
}
