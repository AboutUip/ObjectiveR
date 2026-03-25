# 实现范围（相对 `docs/obr/` 全集）

本节列出 **当前 BlinkEngine 源码** 中可依赖的行为；未列出的规范条目视为**未实现、不完整或可能偏差**，以 `src/test` 与 `com.kitepromiss.obr` 源码为准。分阶段细节见 [parsing.md](parsing.md)、[semantic-binding.md](semantic-binding.md)、[execution.md](execution.md)、[lexing.md](lexing.md) 等。

---

## 语言版本

- `ObrLanguageVersion.SUPPORTED` 与 `VersionDirectiveChecker` 一致；非支持版本号 → `E_VER_UNSUPPORTED`（见 [errors.md](errors.md)）。

---

## 顶层与模块

- `.obr`：`deRfun`、预处理行、`import`（由 `Parser` / `ObrProgramLoader` 覆盖的范围）。
- **项目根**：Blink 仅按磁盘上的 `main.obr` 父目录确定 `projectRoot`；**不**解析 `#LINK` 改写根目录（与 `docs/obr/runtime.md` 一般情形可能不一致）。详见 [project-preproc.md](project-preproc.md)。
- `.mr`：声明、`namespace`（`Parser#parseMrFile`）。
- `import` 解析与 `MrModuleIndex`：模块名唯一路径；冲突抛错（消息无前缀码，见 [errors.md](errors.md)）。

---

## 语句（解析 + 语义 + 运行）

| 能力 | 说明 |
|------|------|
| `if ( Cond ) Stmt` / `else` | `Parser` 产出 `Stmt.If`；`else if` 为 `else` + 内层 `if`。条件须为可参与布尔上下文的类型（见 `SemanticBinder`）。 |
| 空语句 `;` | `Stmt.Nop`。 |
| 既有语句 | `Expression`（调用）、`Return`、`Block`、`VarDecl`、`Assign`（`=` 与复合赋值）、`Update`、`StaticMark` 等，行为见各专题文档。 |

---

## 表达式（解析 + 语义 + 运行）

### 运算符与优先级

与 [`docs/obr/operators.md`](../obr/operators.md) 一致的方向：`Parser` 中自顶向下为 **`?:`** → **`||`** → **`&&`** → **相等**（`==` `!=`）→ **关系**（`<` `<=` `>` `>=`）→ **加减** → **乘除模** → **幂 `**`（右结合）** → **一元** → **后缀** → **基本式**。

### 已实现的一类行为

| 类别 | Blink 行为摘要 |
|------|----------------|
| 相等 `==` / `!=` | 静态：两侧类型须一致，且为规范允许的标量/`string`/`char` 等；结果 `boolean`。运行：`string` 为**引用**相等（见 `RuntimeExecutor`）。 |
| 关系 `<` `<=` `>` `>=` | 静态：两侧类型须一致，且为 `byte`/`short`/`int`/`long`；结果 `boolean`。运行：按 `int`/`long` 比较路径（值承载为 `Value` 的整型族）。 |
| 逻辑 `&&` / `||` | 静态：操作数须可用于布尔上下文；结果 `boolean`。运行：**短路**求值。 |
| 三元 `?:` | 静态：条件可用于布尔上下文；**两分支类型须一致**。运行：先求条件再求所选分支。 |
| 算术与 `**` | 与 `NumericExprTyping`、`SemanticBinder#inferArithmeticType` / `#inferPowType` 一致；**`byte` 与除 `byte` 外的其它数值不得在同一二元算术 / `**`（非 byte-byte）中混用**（静态 `E_SEM_TYPE_INFER_EXPR`）。 |
| `+` | 数值提升或 **`string`/`char` 与允许侧拼接**（含数字转字符串）；静态规则见 `SemanticBinder#inferAddType`。 |
| 复合赋值 | `string` 仅支持 `+=`；`byte` 左侧复合赋值右侧须为 `byte`；其它见 `SemanticBinder` 赋值分支。 |

### 词法

- `==`、`!=`、`<`、`<=`、`>`、`>=`、`&&`、`||`、`?`（三元）。
- 关键字 `if`、`else`。
- 单独的 `&` 或 `|`：**报错**并提示使用 `&&` / `||`（见 [lexing.md](lexing.md)）。

---

## 运行时（节选）

- **整型除零 / 取模除零**：`int` / `long` 的 `/` 与 `%` 在除数为 0 时 → **`E_RT_INTEGER_DIV_ZERO`**（见 [errors.md](errors.md)）。`float`/`double` 按 IEEE 754，**不**抛该码。
- **`if` 与 void 尾调用**：`if` 分支内的语句由 `executeStmtWithoutBlock` 等处理；若分支末条产生 void **尾调用**，仍经 `StmtExecResult.TailVoidCall` 与外层 `call` 循环承接（见 [execution.md](execution.md)）。
- **未初始化 / `undefined`**：参与关系运算等路径时，若运行时值为 `UNDEFINED`，可能触发运行时消息（如「关系运算期望整数标量」）；依赖规范中 `static` / 初始化顺序的代码应先保证变量已赋值或已执行初始化函数（见 [`docs/obr/runtime.md`](../obr/runtime.md)）。

---

## 调用与重载

- 声明键：`FunctionSignature`（限定名 + 参数类型关键字列表）。
- 数值重载：`NumericWidening` + `SemanticBinder.resolveByNumericWiden` + `RuntimeExecutor.resolveRuntimeSignature`（见 [overload-resolution.md](overload-resolution.md)）。**规范**：[`docs/obr/moduleR.md`](../obr/moduleR.md) §7.9 第 2 步（**仅调用点**加宽；与 `operators.md` §1.2 表达式规则独立）。

---

## 调用栈、`return`、`static var`

- **调用栈**：`RuntimeExecutor` 内 `ArrayDeque<Frame>`；超出 `maxCallDepth` → `E_RT_STACK_OVERFLOW`（见 [errors.md](errors.md)）。
- **void 尾调用（TCO）**：块末单独 void `deRfun` 调用由 `call` 外层循环承接（见 [execution.md](execution.md)）。
- **`return`**：非 void 须执行到 `Stmt.Return`；否则 `E_RT_RETURN_MISSING`；返回类型数值加宽失败 `E_RT_RETURN_COERCE`。
- **`static var`**：再次执行声明且同名槽已存在 → `E_RT_STATIC_LOCAL_REDECL`（见 [`docs/obr/runtime.md`](../obr/runtime.md) §5.1）。

---

## 回归测试（可观察行为）

`src/test/java` 中与 Blink 行为对应的类见 [testing.md](testing.md)（含 `BlinkRuntimeE2ETest`、`BlinkSemanticNegativeTest`、`BlinkLexerParserTest`、`NumericExprTypingTest` 及 `testsupport.BlinkObrTestSupport`）。

---

## 仍可能未覆盖或与规范有意的差异（非穷举）

- **位运算**（除一元 `~` 等已有部分）：以源码 `Parser`/`evalExpr` 为准。
- **`#LINK` 与项目根**：见 [project-preproc.md](project-preproc.md)。
- **更细的 `undefined` / `null` 与运算符组合**：以 `SemanticBinder` 与 `RuntimeExecutor` 当前分支为准。

具体以 `Lexer`、`Parser`、`SemanticBinder`、`NumericExprTyping`、`RuntimeExecutor` 为准。
