# 源码结构（`com.kitepromiss.obr`）

路径相对 `src/main/java/`。下表：**包** → **持久或关键状态** → **入口方法** → **文档**。

---

## `com.kitepromiss`

| 类 | 状态 | 入口 | 文档 |
|----|------|------|------|
| `Main` | — | `main` | [pipeline.md](pipeline.md) |

---

## `com.kitepromiss.obr`

| 类 | 状态 | 入口 | 文档 |
|----|------|------|------|
| `ObrInterpreter` | `InterpreterAuditLog` | `run(LaunchArgs)` | [pipeline.md](pipeline.md) |
| `LaunchArgs` | — | `parse` | [pipeline.md](pipeline.md) |
| `ObrException` | — | 构造 / `extractErrorCode` | [errors.md](errors.md)、[supporting.md](supporting.md) |

---

## `obr.project`

| 类 | 状态 | 入口 | 文档 |
|----|------|------|------|
| `ProjectLocator` | — | `resolve` | [modules.md](modules.md) |
| `ProjectResolution` | `record(mainObr, projectRoot)` | — | [supporting.md](supporting.md) |

---

## `obr.lex`

| 类 | 状态 | 入口 | 文档 |
|----|------|------|------|
| `Lexer` | `pos`、行列 | `readAllTokens` → `nextToken` | [lexing.md](lexing.md) |
| `Token` / `TokenKind` | — | — | [lexing.md](lexing.md) |
| `CharLiteralParser` | — | `parseCharLexeme` | [lexing.md](lexing.md)、[supporting.md](supporting.md) |

---

## `obr.parse`

| 类 | 状态 | 入口 | 文档 |
|----|------|------|------|
| `Parser` | `TokenCursor` | `parseObrFile` / `parseMrFile` | [parsing.md](parsing.md) |
| `TokenCursor` | `pos` | `peek` / `advance` / `peekToken` | [parsing.md](parsing.md) |

---

## `obr.ast`

| 内容 | 文档 |
|------|------|
| `ObrFile`、`MrFile`、`Stmt`、`Expr`、`VarVisibility` 等 | [parsing.md](parsing.md) |

---

## `obr.module`

| 类 | 状态 | 入口 | 文档 |
|----|------|------|------|
| `LibsProvisioner` | — | `ensure` / `cleanup` | [modules.md](modules.md) |
| `ObrProgramLoader` | — | `loadAllObr` | [modules.md](modules.md) |
| `ObrProgramBundle` | `ParsedObrFile` 列表 | `record` | [modules.md](modules.md) |
| `MrModuleIndex` | 模块名→路径 | `scan` / `require` | [modules.md](modules.md) |
| `ModuleLoader` | — | `load` | [modules.md](modules.md) |
| `ModuleBundle` | 已装载模块序 | `of` | [modules.md](modules.md) |
| `LoadedMrModule` | 模块名、路径、`MrFile` | — | [modules.md](modules.md) |

---

## `obr.semantic`

| 类 | 状态 | 入口 | 文档 |
|----|------|------|------|
| `VersionDirectiveChecker` | — | `checkProgram` | [version-directive.md](version-directive.md) |
| `ObrLanguageVersion` | `SUPPORTED` 常量 | — | [version-directive.md](version-directive.md) |
| `SemanticBinder` | — | `bindObrFile`、`mergeProgramFileStatics`、`assertUniqueDeRfunDefinitions` | [semantic-binding.md](semantic-binding.md) |
| `FileStaticRegistry` | 每 `.obr` 文件 static 登记 | `collect` | [semantic-binding.md](semantic-binding.md) |
| `NumericWidening` | 纯函数 | `oneArgCost`、`totalWideningCost` | [overload-resolution.md](overload-resolution.md) |
| `FunctionSignature` | `qualifiedName` + `paramTypes` | — | [semantic-binding.md](semantic-binding.md)、[overload-resolution.md](overload-resolution.md) |
| `DeclaredFunction` | 模块名 + 声明 | — | [semantic-binding.md](semantic-binding.md) |

---

## `obr.runtime`

| 类 | 状态 | 入口 | 文档 |
|----|------|------|------|
| `RuntimeExecutor` | `defs`、`defOrigins`、`stack`、`functionStaticStores`、`fileStaticSlotsByName` | `executeMain` → `call` → `executeStmt` / `evalExpr` | [execution.md](execution.md) |

---

## `obr.trace`

| 类 | 状态 | 入口 | 文档 |
|----|------|------|------|
| `InterpreterAuditLog` | 策略 | `event` | [audit.md](audit.md) |
| `TraceLevel` / `TraceCategory` | 枚举 | — | [audit.md](audit.md) |

---

## AST 根与解释器接线

| 输入 | 方法 | 根类型 |
|------|------|--------|
| `.obr` | `Parser#parseObrFile` | `ObrFile` |
| `.mr` | `Parser#parseMrFile` | `MrFile` |

`ObrInterpreter`：`mergeProgramFileStatics(program)` → 对每个 `ParsedObrFile`：`ModuleLoader.load` → `SemanticBinder.bindObrFile` → `RuntimeExecutor.executeMain`。

---

## 内建

`std::rout`：见 [execution.md](execution.md)。
