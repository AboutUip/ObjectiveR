<div align="center"><img src="../assets/obr-logo.svg" alt="Obr" width="48" height="48"/></div>

# BlinkEngine（`docs/blink/`）

**包**：`com.kitepromiss.obr` · **CLI**：`com.kitepromiss.Main` · **入口**：`com.kitepromiss.obr.ObrInterpreter`

**语言规范**：`docs/obr/`。可观察行为以源码与 `src/test` 为准。

---

## 体例

- 正文不标注「新增」；变更以版本控制为准。
- 类名、方法名、错误前缀与源码一致；`E_*` 清单见 [errors.md](errors.md)。

---

## 文档职责（全量）

| 文档 | 覆盖范围 |
|------|----------|
| [pipeline.md](pipeline.md) | `Main`、`LaunchArgs`、`ObrInterpreter#run` 阶段顺序、`finally` |
| [lexing.md](lexing.md) | `Lexer`、`Token`、`TokenKind`、`CharLiteralParser`（摄入） |
| [parsing.md](parsing.md) | `Parser`、`TokenCursor`、`ObrFile`/`MrFile`/`Stmt`/`Expr`（摄入） |
| [modules.md](modules.md) | `ProjectLocator`、`LibsProvisioner`、`ObrProgramLoader`、`ObrProgramBundle`、`MrModuleIndex`、`ModuleLoader`、`ModuleBundle` |
| [version-directive.md](version-directive.md) | `VersionDirectiveChecker`、`ObrLanguageVersion` |
| [semantic-binding.md](semantic-binding.md) | `SemanticBinder`、`FileStaticRegistry`、`mergeProgramFileStatics`、声明/调用/`return` |
| [overload-resolution.md](overload-resolution.md) | `NumericWidening`、语义与运行时重载 |
| [execution.md](execution.md) | `RuntimeExecutor`：`call`、`evalStmt`、`evalExpr`、栈、`std::rout`、静态槽 |
| [architecture.md](architecture.md) | 子包索引与数据流 |
| [implementation-scope.md](implementation-scope.md) | 相对 `docs/obr/` 全集的边界 |
| [errors.md](errors.md) | `E_*` 与无前缀异常 |
| [audit.md](audit.md) | `InterpreterAuditLog`、`TraceLevel`、`TraceCategory`、各阶段 `phase` |
| [supporting.md](supporting.md) | 管线配角类型（`ObrException`、`ProjectResolution`、record 等） |
| [inventory.md](inventory.md) | 每 `.java` → 首选文档 |

---

## 追溯（行为 → 文档 → 源码）

| 行为 | 文档 | 源码 |
|------|------|------|
| 入口与 `main.obr` | [pipeline.md](pipeline.md)、[modules.md](modules.md) | `ProjectLocator` |
| `libs/` 重建与退出清理 | [pipeline.md](pipeline.md)、[modules.md](modules.md) | `LibsProvisioner`、`ObrInterpreter` |
| 词法 / 语法 | [lexing.md](lexing.md)、[parsing.md](parsing.md) | `Lexer`、`Parser` |
| 多 `.obr` / `.mr` | [modules.md](modules.md) | `ObrProgramLoader`、`MrModuleIndex`、`ModuleLoader` |
| `#VERSION` | [version-directive.md](version-directive.md) | `VersionDirectiveChecker` |
| 语义 | [semantic-binding.md](semantic-binding.md) | `SemanticBinder`、`FileStaticRegistry` |
| 数值重载 | [overload-resolution.md](overload-resolution.md) | `NumericWidening` |
| 运行 | [execution.md](execution.md) | `RuntimeExecutor` |
| 错误码 | [errors.md](errors.md) | 各常量定义处 |
| 审计 | [audit.md](audit.md) | `InterpreterAuditLog.event` |

**变更代码时**：同步相关专题与 [inventory.md](inventory.md)。
