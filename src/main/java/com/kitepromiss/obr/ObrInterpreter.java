package com.kitepromiss.obr;

import com.kitepromiss.obr.ast.ObrFile;
import com.kitepromiss.obr.lex.Lexer;
import com.kitepromiss.obr.module.LibsProvisioner;
import com.kitepromiss.obr.module.ModuleBundle;
import com.kitepromiss.obr.module.ModuleLoader;
import com.kitepromiss.obr.module.ObrProgramBundle;
import com.kitepromiss.obr.module.ObrProgramLoader;
import com.kitepromiss.obr.lex.Token;
import com.kitepromiss.obr.parse.Parser;
import com.kitepromiss.obr.project.ProjectLocator;
import com.kitepromiss.obr.project.ProjectResolution;
import com.kitepromiss.obr.runtime.RuntimeExecutor;
import com.kitepromiss.obr.semantic.ProgramLinkIndex;
import com.kitepromiss.obr.semantic.SemanticBinder;
import com.kitepromiss.obr.semantic.VersionDirectiveChecker;
import com.kitepromiss.obr.trace.InterpreterAuditLog;
import com.kitepromiss.obr.trace.TraceCategory;
import com.kitepromiss.obr.trace.TraceLevel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * BlinkEngine：ObjectiveR（Obr）语言的参考解释器入口；所有阶段通过 {@link InterpreterAuditLog} 输出可审查记录。
 */
public final class ObrInterpreter {

    private final InterpreterAuditLog audit;

    public ObrInterpreter() {
        this(InterpreterAuditLog.silent());
    }

    public ObrInterpreter(InterpreterAuditLog audit) {
        this.audit = audit;
    }

    /**
     * @return 进程退出码：0 成功，1 错误
     */
    public int run(LaunchArgs la) {
        String runId = UUID.randomUUID().toString();
        Path projectRoot = null;
        try {
            audit.event(
                    TraceLevel.SUMMARY,
                    TraceCategory.BOOT,
                    "interpreter_start",
                    InterpreterAuditLog.fields(
                            "run_id", runId,
                            "trace_policy", la.tracePolicy().name(),
                            "input", la.input().toString()));
            ProjectResolution pr = ProjectLocator.resolve(la.input());
            projectRoot = pr.projectRoot();
            audit.event(
                    TraceLevel.NORMAL,
                    TraceCategory.FILES,
                    "project_resolved",
                    InterpreterAuditLog.fields(
                            "main_obr", pr.mainObr().toAbsolutePath().normalize().toString(),
                            "project_root", pr.projectRoot().toAbsolutePath().normalize().toString()));

            LibsProvisioner.ensure(pr.projectRoot(), audit);
            audit.event(
                    TraceLevel.NORMAL,
                    TraceCategory.MODULES,
                    "libs_ensured",
                    InterpreterAuditLog.fields(
                            "path",
                            pr.projectRoot().resolve(LibsProvisioner.LIBS_DIR).toAbsolutePath().normalize().toString()));

            Path main = pr.mainObr();
            String src = Files.readString(main);
            audit.event(
                    TraceLevel.NORMAL,
                    TraceCategory.LEX,
                    "lex_file_begin",
                    InterpreterAuditLog.fields(
                            "path", main.toString(),
                            "char_count", Integer.toString(src.length())));

            List<Token> tokens = Lexer.readAllTokens(src, main.toString(), audit);
            audit.event(
                    TraceLevel.NORMAL,
                    TraceCategory.LEX,
                    "lex_file_end",
                    InterpreterAuditLog.fields(
                            "path", main.toString(),
                            "token_count_including_eof", Integer.toString(tokens.size())));

            audit.event(
                    TraceLevel.NORMAL,
                    TraceCategory.PARSE,
                    "parse_main_obr_begin",
                    InterpreterAuditLog.fields("path", main.toString()));
            ObrFile ast = new Parser(main.toString(), tokens).parseObrFile();
            audit.event(
                    TraceLevel.NORMAL,
                    TraceCategory.PARSE,
                    "parse_main_obr_end",
                    InterpreterAuditLog.fields(
                            "path", main.toString(),
                            "top_level_items", Integer.toString(ast.items().size())));

            ObrProgramBundle program = ObrProgramLoader.loadAllObr(pr.projectRoot(), main, ast, audit);
            audit.event(
                    TraceLevel.NORMAL,
                    TraceCategory.FILES,
                    "obr_program_loaded",
                    InterpreterAuditLog.fields("obr_files", Integer.toString(program.files().size())));
            audit.event(
                    TraceLevel.NORMAL,
                    TraceCategory.SEMANTIC,
                    "bind_program_begin",
                    InterpreterAuditLog.fields(
                            "run_id", runId,
                            "obr_files", Integer.toString(program.files().size())));
            VersionDirectiveChecker.checkProgram(program);
            SemanticBinder.assertUniqueDeRfunDefinitions(program);
            var programFileStatics = SemanticBinder.mergeProgramFileStatics(program);
            ProgramLinkIndex linkIndex = ProgramLinkIndex.from(program, pr.projectRoot());
            for (ObrProgramBundle.ParsedObrFile f : program.files()) {
                ModuleBundle modules = ModuleLoader.load(pr.projectRoot(), f.ast(), audit);
                audit.event(
                        TraceLevel.NORMAL,
                        TraceCategory.MODULES,
                        "module_bundle_ready",
                        InterpreterAuditLog.fields(
                                "path", f.path().toString(),
                                "modules_loaded", Integer.toString(modules.loadOrder().size())));
                SemanticBinder.bindObrFile(
                        pr.projectRoot(), f.path(), f.ast(), modules, audit, runId, linkIndex, programFileStatics);
            }
            audit.event(
                    TraceLevel.NORMAL,
                    TraceCategory.SEMANTIC,
                    "bind_program_end",
                    InterpreterAuditLog.fields(
                            "run_id", runId,
                            "obr_files", Integer.toString(program.files().size())));

            audit.event(
                    TraceLevel.NORMAL,
                    TraceCategory.RUNTIME,
                    "runtime_main_begin",
                    InterpreterAuditLog.fields("run_id", runId, "path", main.toString()));
            new RuntimeExecutor(audit, System.out, 1024, runId).executeMain(program);
            audit.event(
                    TraceLevel.NORMAL,
                    TraceCategory.RUNTIME,
                    "runtime_main_end",
                    InterpreterAuditLog.fields("run_id", runId, "path", main.toString()));

            audit.event(
                    TraceLevel.SUMMARY,
                    TraceCategory.BOOT,
                    "interpreter_exit",
                    InterpreterAuditLog.fields("run_id", runId, "code", "0"));
            return 0;
        } catch (ObrException e) {
            String code = ObrException.extractErrorCode(e.getMessage());
            audit.event(
                    TraceLevel.SUMMARY,
                    TraceCategory.BOOT,
                    "interpreter_error",
                    InterpreterAuditLog.fields(
                            "run_id", runId,
                            "error_code", code,
                            "message", e.getMessage()));
            System.err.println(e.getMessage());
            return 1;
        } catch (IOException e) {
            String msg = "读取源文件失败: " + e.getMessage();
            audit.event(
                    TraceLevel.SUMMARY,
                    TraceCategory.BOOT,
                    "interpreter_error",
                    InterpreterAuditLog.fields(
                            "run_id", runId,
                            "error_code", "E_IO_READ",
                            "message", msg));
            System.err.println(msg);
            return 1;
        } finally {
            if (projectRoot != null) {
                try {
                    LibsProvisioner.cleanup(projectRoot, audit);
                } catch (ObrException ex) {
                    System.err.println(ex.getMessage());
                }
            }
        }
    }
}
