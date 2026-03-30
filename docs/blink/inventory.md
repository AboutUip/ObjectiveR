# 源码与文档对照（`com.kitepromiss.obr`）

本表将 **每个 Java 源文件** 映射到 `docs/blink/` 中说明其行为的首选文档，便于开源审阅与变更时同步更新文档。

| 源文件 | 说明文档 |
|--------|----------|
| `ObrInterpreter.java` | [pipeline.md](pipeline.md) |
| `LaunchArgs.java` | [pipeline.md](pipeline.md) |
| `ObrException.java` | [supporting.md](supporting.md)、[errors.md](errors.md) |
| `project/ProjectLocator.java` | [modules.md](modules.md)、[project-preproc.md](project-preproc.md) |
| `project/ProjectResolution.java` | [supporting.md](supporting.md)、[modules.md](modules.md) |
| `project/LinkParser.java`、`project/LinkAccess.java` | [project-preproc.md](project-preproc.md) |
| `project/ProjectRootResolver.java` | [project-preproc.md](project-preproc.md) |
| `lex/Lexer.java` | [lexing.md](lexing.md)（`#LINK` 逗号续行合并见 `project/LinkParser.java`） |
| `lex/Token.java` | [lexing.md](lexing.md)、[supporting.md](supporting.md) |
| `lex/TokenKind.java` | [lexing.md](lexing.md) |
| `lex/CharLiteralParser.java` | [supporting.md](supporting.md)、[lexing.md](lexing.md) |
| `parse/Parser.java` | [parsing.md](parsing.md) |
| `parse/TokenCursor.java` | [parsing.md](parsing.md) |
| `ast/*`（`ObrFile`、`MrFile`、`Stmt`、`Expr`、`CallExpr` 等） | [ast-reference.md](ast-reference.md)、[parsing.md](parsing.md)、[architecture.md](architecture.md) |
| `module/LibsProvisioner.java` | [modules.md](modules.md) |
| `module/ObrProgramLoader.java` | [modules.md](modules.md)、[project-preproc.md](project-preproc.md) |
| `module/ObrProgramBundle.java` | [modules.md](modules.md)、[supporting.md](supporting.md) |
| `module/ModuleLoader.java` | [modules.md](modules.md) |
| `module/ModuleBundle.java` | [modules.md](modules.md)、[supporting.md](supporting.md) |
| `module/LoadedMrModule.java` | [modules.md](modules.md)、[supporting.md](supporting.md) |
| `module/MrModuleIndex.java` | [modules.md](modules.md) |
| `semantic/SemanticBinder.java` | [semantic-binding.md](semantic-binding.md)（`bindObrFile`、`checkAssignSemantics`、`checkExprStmtExpr`、`inferType`、循环 `loopDepth`） |
| `semantic/FileStaticRegistry.java` | [semantic-binding.md](semantic-binding.md) |
| `semantic/ProgramLinkIndex.java` | [project-preproc.md](project-preproc.md)、[semantic-binding.md](semantic-binding.md) |
| `semantic/VersionDirectiveChecker.java` | [version-directive.md](version-directive.md) |
| `semantic/NumericWidening.java` | [overload-resolution.md](overload-resolution.md) |
| `semantic/NumericExprTyping.java` | [semantic-binding.md](semantic-binding.md)、[implementation-scope.md](implementation-scope.md) |
| `semantic/ObrLanguageVersion.java` | [version-directive.md](version-directive.md)、[supporting.md](supporting.md) |
| `semantic/FunctionSignature.java` | [semantic-binding.md](semantic-binding.md)、[overload-resolution.md](overload-resolution.md)、[supporting.md](supporting.md) |
| `semantic/DeclaredFunction.java` | [semantic-binding.md](semantic-binding.md)、[supporting.md](supporting.md) |
| `runtime/RuntimeExecutor.java` | [execution.md](execution.md)、[runtime-model.md](runtime-model.md)（`evalExpr`、`evalAssignExpression`、`evalExprStmtDiscard`、`call`、TCO） |
| `trace/InterpreterAuditLog.java` | [audit.md](audit.md)、[supporting.md](supporting.md) |
| `trace/TraceLevel.java` | [audit.md](audit.md)、[supporting.md](supporting.md) |
| `trace/TraceCategory.java` | [audit.md](audit.md) |

**CLI 入口**（`com.kitepromiss.Main`）：[pipeline.md](pipeline.md)。

**测试代码**：`src/test/java/com/kitepromiss/obr/` 与上表镜像；索引见 [testing.md](testing.md)。行为以测试类名为准，重大行为须在对应专题文档可印证。
