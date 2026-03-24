package com.kitepromiss.obr.module;

import com.kitepromiss.obr.ObrException;
import com.kitepromiss.obr.ast.ObrFile;
import com.kitepromiss.obr.lex.Lexer;
import com.kitepromiss.obr.lex.Token;
import com.kitepromiss.obr.parse.Parser;
import com.kitepromiss.obr.trace.InterpreterAuditLog;
import com.kitepromiss.obr.trace.TraceCategory;
import com.kitepromiss.obr.trace.TraceLevel;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/** 扫描并解析项目内所有 .obr 文件，供运行时建立函数定义表。 */
public final class ObrProgramLoader {

    private ObrProgramLoader() {}

    public static ObrProgramBundle loadAllObr(Path projectRoot, Path mainPath, ObrFile mainAst, InterpreterAuditLog audit) {
        List<ObrProgramBundle.ParsedObrFile> parsed = new ArrayList<>();
        Path mainNorm = mainPath.toAbsolutePath().normalize();
        parsed.add(new ObrProgramBundle.ParsedObrFile(mainNorm, mainAst));
        try {
            Files.walkFileTree(
                    projectRoot.toAbsolutePath().normalize(),
                    new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                            String n = dir.getFileName().toString();
                            if (n.equals(".git")
                                    || n.equals("target")
                                    || n.equals("build")
                                    || n.equals(".idea")
                                    || n.equals("node_modules")) {
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
                            if (!fn.endsWith(".obr")) {
                                return FileVisitResult.CONTINUE;
                            }
                            Path norm = file.toAbsolutePath().normalize();
                            if (norm.equals(mainNorm)) {
                                return FileVisitResult.CONTINUE;
                            }
                            try {
                                String src = Files.readString(norm);
                                List<Token> tok = Lexer.readAllTokens(src, norm.toString(), audit);
                                ObrFile ast = new Parser(norm.toString(), tok).parseObrFile();
                                parsed.add(new ObrProgramBundle.ParsedObrFile(norm, ast));
                                audit.event(
                                        TraceLevel.NORMAL,
                                        TraceCategory.FILES,
                                        "obr_loaded",
                                        InterpreterAuditLog.fields("path", norm.toString()));
                            } catch (IOException e) {
                                throw new ObrException("读取 .obr 失败: " + norm + " — " + e.getMessage());
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
        } catch (IOException e) {
            throw new ObrException("扫描 .obr 失败: " + e.getMessage());
        }
        return new ObrProgramBundle(mainNorm, mainAst, List.copyOf(parsed));
    }
}
