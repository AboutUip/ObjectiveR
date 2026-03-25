<div align="center"><img src="../assets/obr-logo.svg" alt="Obr" width="48" height="48"/></div>

# BlinkEngine（`docs/blink/`）

**包**：`com.kitepromiss.obr` · **CLI**：`com.kitepromiss.Main` · **入口**：`com.kitepromiss.obr.ObrInterpreter`

**语言规范**：`docs/obr/`。可观察行为以源码与 `src/test` 为准。

**全逻辑入口**（按主题查文档与源码）：[logic-index.md](logic-index.md)。

---

## 体例

- 正文不标注「新增」；变更以版本控制为准。
- 类名、方法名、错误前缀与源码一致；`E_*` 清单见 [errors.md](errors.md)。

---

## 文档职责（全量）

| 文档 | 覆盖范围 |
|------|----------|
| [logic-index.md](logic-index.md) | **总索引**：逻辑主题 → 专题文档 → 类/方法 |
| [pipeline.md](pipeline.md) | `Main`、`LaunchArgs`、`ObrInterpreter#run` 阶段顺序、`finally` |
| [project-preproc.md](project-preproc.md) | `ProjectLocator`、`ObrProgramLoader` 扫描、预处理行消费与 **`#LINK` 差异** |
| [lexing.md](lexing.md) | `Lexer`、`Token`、`TokenKind`、`CharLiteralParser`（摄入） |
| [parsing.md](parsing.md) | `Parser`、`TokenCursor`、解析分支（摄入） |
| [ast-reference.md](ast-reference.md) | `ast` 包全部节点与枚举字段（对照源码） |
| [modules.md](modules.md) | `LibsProvisioner`、`ObrProgramLoader`、`ObrProgramBundle`、`MrModuleIndex`、`ModuleLoader`、`ModuleBundle` |
| [version-directive.md](version-directive.md) | `VersionDirectiveChecker`、`ObrLanguageVersion` |
| [semantic-binding.md](semantic-binding.md) | `SemanticBinder`、`FileStaticRegistry`、`mergeProgramFileStatics`、声明/调用/`return` |
| [overload-resolution.md](overload-resolution.md) | `NumericWidening`、语义与运行时重载 |
| [execution.md](execution.md) | `RuntimeExecutor`：`call`、`executeStmtsWithTail`、`evalExpr`、栈、`std::rout`、void 尾调用、静态槽 |
| [runtime-model.md](runtime-model.md) | `RuntimeExecutor` 内部 `Value`/`Env`/`StaticStore`/栈 |
| [architecture.md](architecture.md) | 子包索引与数据流 |
| [implementation-scope.md](implementation-scope.md) | 相对 `docs/obr/` 全集的边界 |
| [errors.md](errors.md) | `E_*` 与无前缀异常 |
| [audit.md](audit.md) | `InterpreterAuditLog`、`TraceLevel`、`TraceCategory`、各阶段 `phase` |
| [supporting.md](supporting.md) | 管线配角类型（`ObrException`、`ProjectResolution`、record 等） |
| [testing.md](testing.md) | `src/test` 镜像与测试类索引 |
| [inventory.md](inventory.md) | 每 `.java` → 首选文档 |

---

## 追溯（行为 → 文档 → 源码）

| 行为 | 文档 | 源码 |
|------|------|------|
| 全主题检索 | [logic-index.md](logic-index.md) | — |
| 入口与 `main.obr`、扫描与 `#LINK` | [pipeline.md](pipeline.md)、[project-preproc.md](project-preproc.md)、[modules.md](modules.md) | `ProjectLocator`、`ObrProgramLoader` |
| `libs/` 重建与退出清理 | [pipeline.md](pipeline.md)、[modules.md](modules.md) | `LibsProvisioner`、`ObrInterpreter` |
| 词法 / 语法 / AST 字段 | [lexing.md](lexing.md)、[parsing.md](parsing.md)、[ast-reference.md](ast-reference.md) | `Lexer`、`Parser`、`ast/*` |
| 多 `.obr` / `.mr` | [modules.md](modules.md) | `ObrProgramLoader`、`MrModuleIndex`、`ModuleLoader` |
| `#VERSION` | [version-directive.md](version-directive.md) | `VersionDirectiveChecker` |
| 语义 | [semantic-binding.md](semantic-binding.md) | `SemanticBinder`、`FileStaticRegistry` |
| 数值重载 | [overload-resolution.md](overload-resolution.md) | `NumericWidening` |
| 运行、void 尾调用（TCO） | [execution.md](execution.md)、[runtime-model.md](runtime-model.md)、[`docs/obr/runtime.md`](../obr/runtime.md) §5.2 | `RuntimeExecutor`（`call`、`executeStmtsWithTail`） |
| 测试 | [testing.md](testing.md) | `src/test/java/com/kitepromiss/obr/` |
| 错误码 | [errors.md](errors.md) | 各常量定义处 |
| 审计 | [audit.md](audit.md) | `InterpreterAuditLog.event` |

**变更代码时**：同步相关专题与 [inventory.md](inventory.md)。
