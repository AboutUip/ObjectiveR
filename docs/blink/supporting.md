# 支撑类型与工具

本节说明 **非整条管线主角**、但被多处依赖的类型与工具类；与专题文档交叉引用。

---

## `ObrException`（`com.kitepromiss.obr`）

- 运行时异常；消息常带 `E_*` 前缀供 [errors.md](errors.md) 与 `extractErrorCode` 使用。
- `extractErrorCode(String message)`：取首个空格前片段，匹配 `^E_[A-Z0-9_]+$` 则返回，否则 `UNKNOWN`。

---

## `ProjectResolution`（`project`）

- `record(Path mainObr, Path projectRoot)`：入口文件与项目根（见 [modules.md](modules.md)）。

---

## `Token`（`lex`）

- `record(TokenKind kind, String lexeme, int line, int column)`。

---

## `CharLiteralParser`（`lex`）

- 自 `Lexer` 产出的 char 字面量 lexeme 做合法性校验；非法抛 `ObrException`（消息可被 `Lexer` 包装为带文件行列）。

---

## `ObrLanguageVersion`（`semantic`）

- `SUPPORTED`：当前解释器唯一支持的 Obr 语言版本号（与 `#VERSION` 校验一致）；见 [version-directive.md](version-directive.md)。

---

## `FunctionSignature` / `DeclaredFunction`（`semantic`）

- `FunctionSignature`：`qualifiedName` + `paramTypes`（`List<String>` 类型关键字），**不含**返回类型；重载键。见 [semantic-binding.md](semantic-binding.md)、[overload-resolution.md](overload-resolution.md)。
- `DeclaredFunction`：模块名 + `MrItem.DeRfunDecl`。

---

## `FileStaticRegistry`（`semantic`）

- 扫描单 `.obr` 内 static 槽；`collect` → 合并为程序级映射。见 [semantic-binding.md](semantic-binding.md)。

---

## `ObrProgramBundle` / `LoadedMrModule` / `ModuleBundle`（`module`）

- `ObrProgramBundle`：`mainPath`、`mainAst`、`List<ParsedObrFile>`；见 [modules.md](modules.md)。
- `LoadedMrModule`：模块名、路径、`MrFile`。
- `ModuleBundle`：`loadOrder()` 返回已装载模块列表。

---

## `InterpreterAuditLog` / `TraceLevel`（`trace`）

- `InterpreterAuditLog`：`silent()`、`toStdout(TraceLevel policy)`；`event(level, category, phase, fields)`。
- `DefaultInterpreterAuditLog`：单行键值输出；`TraceLevel#emits` 控制是否打印。
- 详见 [audit.md](audit.md)。
