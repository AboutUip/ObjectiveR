package com.kitepromiss.obr.module;

import com.kitepromiss.obr.ObrException;
import com.kitepromiss.obr.trace.InterpreterAuditLog;
import com.kitepromiss.obr.trace.TraceCategory;
import com.kitepromiss.obr.trace.TraceLevel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * 按 {@code docs/obr/system.md} 在项目根下维护 {@code libs/}，仅允许托管 {@code system.mr} 与 {@code system.obr}。
 */
public final class LibsProvisioner {

    public static final String LIBS_DIR = "libs";
    public static final String SYSTEM_MR = "system.mr";
    public static final String SYSTEM_OBR = "system.obr";
    /** {@code import system} / 隐式 system 对应的模块名（不含 .mr）。 */
    public static final String SYSTEM_MODULE_NAME = "system";

    private static final String CANONICAL_SYSTEM_MR =
            """
            @Callfun(!*)
            @Overwrite(*/main.obr)
            deRfun main():void;

            namespace std {
                @Overwrite(libs/system.obr)
                deRfun rout([string]out):void;
                @Overwrite(libs/system.obr)
                deRfun rout([byte]out):void;
                @Overwrite(libs/system.obr)
                deRfun rout([short]out):void;
                @Overwrite(libs/system.obr)
                deRfun rout([int]out):void;
                @Overwrite(libs/system.obr)
                deRfun rout([long]out):void;
                @Overwrite(libs/system.obr)
                deRfun rout([char]out):void;
                @Overwrite(libs/system.obr)
                deRfun rout([float]out):void;
                @Overwrite(libs/system.obr)
                deRfun rout([double]out):void;
            }
            """;

    private static final String CANONICAL_SYSTEM_OBR =
            """
            deRfun std::rout([string]out):void{
            }
            deRfun std::rout([byte]out):void{
            }
            deRfun std::rout([short]out):void{
            }
            deRfun std::rout([int]out):void{
            }
            deRfun std::rout([long]out):void{
            }
            deRfun std::rout([char]out):void{
            }
            deRfun std::rout([float]out):void{
            }
            deRfun std::rout([double]out):void{
            }
            """;

    private LibsProvisioner() {}

    /**
     * 确保 {@code projectRoot/libs} 存在且仅含规范所列托管文件，内容与规范一致。
     *
     * <p>若已存在 {@code libs/}，则<strong>整目录删除后重建</strong>（覆盖），再写入规范内容。
     */
    public static void ensure(Path projectRoot) {
        ensure(projectRoot, InterpreterAuditLog.silent());
    }

    /**
     * 同 {@link #ensure(Path)}；若原先存在 {@code libs/}，先审计再删除重建。
     */
    public static void ensure(Path projectRoot, InterpreterAuditLog audit) {
        Path libs = projectRoot.resolve(LIBS_DIR);
        try {
            if (Files.exists(libs)) {
                audit.event(
                        TraceLevel.NORMAL,
                        TraceCategory.MODULES,
                        "libs_replace_existing",
                        InterpreterAuditLog.fields(
                                "path",
                                libs.toAbsolutePath().normalize().toString(),
                                "reason",
                                "Blink 启动前覆盖 libs"));
                deleteRecursively(libs);
            }
            Files.createDirectories(libs);
            Files.writeString(libs.resolve(SYSTEM_MR), CANONICAL_SYSTEM_MR, StandardCharsets.UTF_8);
            Files.writeString(libs.resolve(SYSTEM_OBR), CANONICAL_SYSTEM_OBR, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ObrException("维护 libs/ 失败: " + e.getMessage());
        }
    }

    /**
     * 删除 {@code projectRoot/libs} 及其全部内容（不存在则忽略）。供解释器一次运行结束后清理。
     */
    public static void cleanup(Path projectRoot) {
        cleanup(projectRoot, InterpreterAuditLog.silent());
    }

    /** 同 {@link #cleanup(Path)}，删除前可写审计事件。 */
    public static void cleanup(Path projectRoot, InterpreterAuditLog audit) {
        Path libs = projectRoot.resolve(LIBS_DIR);
        if (!Files.exists(libs)) {
            return;
        }
        try {
            audit.event(
                    TraceLevel.NORMAL,
                    TraceCategory.MODULES,
                    "libs_cleanup_begin",
                    InterpreterAuditLog.fields(
                            "path", libs.toAbsolutePath().normalize().toString()));
            deleteRecursively(libs);
            audit.event(
                    TraceLevel.NORMAL,
                    TraceCategory.MODULES,
                    "libs_cleanup_done",
                    InterpreterAuditLog.fields(
                            "path", libs.toAbsolutePath().normalize().toString()));
        } catch (IOException e) {
            throw new ObrException("清理 libs/ 失败: " + e.getMessage());
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path p : paths) {
                Files.deleteIfExists(p);
            }
        }
    }
}
