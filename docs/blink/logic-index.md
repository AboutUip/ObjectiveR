# Blink 逻辑速查（全索引）

本页按**逻辑主题**列出：查什么 → 读哪篇文档 → 从哪个类/方法下钻源码。细节仍以各专题正文与 `src/main/java/com/kitepromiss/obr` 为准。

---

## 1. 进程与管线

| 逻辑点 | 文档 | 源码 |
|--------|------|------|
| 退出码、`Main` → `LaunchArgs` → `run` | [pipeline.md](pipeline.md) | `com.kitepromiss.Main`、`LaunchArgs`、`ObrInterpreter#run` |
| 阶段顺序（0–11）、`finally` 清 `libs/` | [pipeline.md](pipeline.md)、[modules.md](modules.md) | `ObrInterpreter`、`LibsProvisioner` |
| 合并 file static、按文件 `ModuleLoader` + `bindObrFile` | [pipeline.md](pipeline.md)、[semantic-binding.md](semantic-binding.md) | `ObrInterpreter`（`mergeProgramFileStatics` 循环） |

---

## 2. 工程路径与预处理（Blink 实际行为）

| 逻辑点 | 文档 | 源码 |
|--------|------|------|
| `main.obr` 定位、项目根、`#LINK` 与规范差异 | [project-preproc.md](project-preproc.md) | `ProjectLocator`、`ProjectResolution` |
| 扫描哪些 `.obr`、跳过哪些目录 | [project-preproc.md](project-preproc.md)、[modules.md](modules.md) | `ObrProgramLoader` |
| 预处理行在 AST 中的形态、谁消费 `#VERSION` | [project-preproc.md](project-preproc.md)、[version-directive.md](version-directive.md) | `Lexer`、`Parser`、`VersionDirectiveChecker` |

---

## 3. 词法

| 逻辑点 | 文档 | 源码 |
|--------|------|------|
| 记号种类、关键字、预处理整行 | [lexing.md](lexing.md) | `TokenKind`、`Lexer` |
| `==` `!=` `<` `<=` `>` `>=` `&&` `||` `?`、`if`/`else`；单 `&` / 单 `|` 报错 | [lexing.md](lexing.md) | `Lexer` |
| `char` 字面量校验 | [lexing.md](lexing.md)、[supporting.md](supporting.md) | `CharLiteralParser` |
| `Token` 记录字段 | [supporting.md](supporting.md) | `Token` |

---

## 4. 语法与 AST

| 逻辑点 | 文档 | 源码 |
|--------|------|------|
| `TokenCursor`、`parseObrFile` / `parseMrFile`、语句分支表（含 `if`、空 `;`） | [parsing.md](parsing.md) | `TokenCursor`、`Parser` |
| 表达式优先级（`?:`、`||`、`&&`、相等/关系、算术…） | [parsing.md](parsing.md) | `Parser`（`parseConditional` 等） |
| 全部 `Expr` / `Stmt` / 顶层项字段与枚举 | [ast-reference.md](ast-reference.md) | `ast` 包下各 `record` / `sealed interface` |

---

## 5. 模块、`libs`、`.mr`

| 逻辑点 | 文档 | 源码 |
|--------|------|------|
| `libs/` 托管文件、`ensure`/`cleanup` | [modules.md](modules.md) | `LibsProvisioner` |
| `MrModuleIndex`、`import` 路径 | [modules.md](modules.md) | `MrModuleIndex` |
| 按 `.obr` 装载 `.mr`、`ModuleBundle` | [modules.md](modules.md) | `ModuleLoader` |
| 程序包 `ObrProgramBundle`、`ParsedObrFile` | [modules.md](modules.md)、[supporting.md](supporting.md) | `ObrProgramBundle` |

---

## 6. 语义与静态表

| 逻辑点 | 文档 | 源码 |
|--------|------|------|
| `bindObrFile`、作用域、`return`、调用检查 | [semantic-binding.md](semantic-binding.md) | `SemanticBinder` |
| 表达式类型推断（相等/关系/逻辑、`?:`、`+` 拼接、byte 混用等） | [semantic-binding.md](semantic-binding.md)、[implementation-scope.md](implementation-scope.md) | `SemanticBinder`、`NumericExprTyping` |
| `FileStaticRegistry.collect`、`Slot`、同函数重复 static | [semantic-binding.md](semantic-binding.md) | `FileStaticRegistry` |
| 程序级合并 `mergeProgramFileStatics` | [semantic-binding.md](semantic-binding.md) | `SemanticBinder` |
| 数值加宽与重载键 | [overload-resolution.md](overload-resolution.md) | `NumericWidening`、`SemanticBinder`、`RuntimeExecutor` |

---

## 7. 运行时

| 逻辑点 | 文档 | 源码 |
|--------|------|------|
| `executeMain`、合并定义、`call`、`std::rout` | [execution.md](execution.md) | `RuntimeExecutor` |
| `if` / 空语句、`evalExpr`（短路、比较、拼接、整型除零 `E_RT_INTEGER_DIV_ZERO`） | [execution.md](execution.md)、[implementation-scope.md](implementation-scope.md) | `RuntimeExecutor` |
| void 尾调用（TCO）、`maxCallDepth` / `E_RT_STACK_OVERFLOW` | [execution.md](execution.md)、[errors.md](errors.md)、[`docs/obr/runtime.md`](../obr/runtime.md) §5.2 | `RuntimeExecutor#call`、`#executeStmtsWithTail` |
| `Value` / `ValueType`、`Env`、`StaticStore`、`Frame`、栈 | [runtime-model.md](runtime-model.md) | `RuntimeExecutor`（内部类型） |
| 语句/表达式求值分支（与 AST 对应） | [execution.md](execution.md) | `RuntimeExecutor#executeStmtWithoutBlock`、`#evalExpr` 等 |

---

## 8. 错误与审计

| 逻辑点 | 文档 | 源码 |
|--------|------|------|
| `E_*` 列表与抛出类 | [errors.md](errors.md) | 各阶段 `throw new ObrException` |
| 无前缀消息、`extractErrorCode` | [errors.md](errors.md)、[supporting.md](supporting.md) | `ObrException` |
| `phase`、级别、类别 | [audit.md](audit.md) | `InterpreterAuditLog`、`TraceLevel`、`TraceCategory` |

---

## 9. 测试

| 逻辑点 | 文档 | 源码 |
|--------|------|------|
| 测试类、`testsupport` 夹具、如何跑 | [testing.md](testing.md) | `src/test/java/com/kitepromiss/obr/`、`com.kitepromiss.obr.testsupport` |

---

## 10. 边界（相对语言规范）

| 逻辑点 | 文档 |
|--------|------|
| 已实现子集、未覆盖运算符等 | [implementation-scope.md](implementation-scope.md) |

---

## 11. 源码文件 → 文档（一对一）

| 逻辑点 | 文档 |
|--------|------|
| 每个 `.java` 首选说明 | [inventory.md](inventory.md) |
| 包级数据流 | [architecture.md](architecture.md) |
