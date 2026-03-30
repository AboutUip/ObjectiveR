# 实现范围（相对 `docs/obr/` 全集）

本节列出 **当前 BlinkEngine 源码** 中可依赖的行为。`docs/obr/` 中**未在本节或各 blink 专题中对照写明**的条文，对 Blink 而言**不得**假定为已实现；最终可观察行为以 **`src/main/java/com/kitepromiss/obr`** 与 **`src/test/java`** 为准。分阶段细节见 [parsing.md](parsing.md)、[semantic-binding.md](semantic-binding.md)、[execution.md](execution.md)、[lexing.md](lexing.md) 等。

---

## 语言版本

- `ObrLanguageVersion.SUPPORTED` 与 `VersionDirectiveChecker` 一致；非支持版本号 → `E_VER_UNSUPPORTED`（见 [errors.md](errors.md)）。

---

## 顶层与模块

- `.obr`：`deRfun`、预处理行、`import`（由 `Parser` / `ObrProgramLoader` 覆盖的范围）。
- **项目根**：`ProjectLocator` 读入口 `main.obr` 源码，`ProjectRootResolver` 按 `docs/obr/runtime.md` §3 与合并后的 `#LINK` 列表确定 `projectRoot`（默认等价 `#LINK /` → 根为 `main.obr` 所在目录；含 `/main/main.obr` 项时校验物理路径并取上两级为根）。详见 [project-preproc.md](project-preproc.md)。
- `.mr`：声明、`namespace`（`Parser#parseMrFile`）。
- `import` 解析与 `MrModuleIndex`：模块名唯一路径；冲突抛错（消息无前缀码，见 [errors.md](errors.md)）。

---

## 语句（解析 + 语义 + 运行）

| 能力 | 说明 |
|------|------|
| `if ( Cond ) Stmt` / `else` | `Parser` 产出 `Stmt.If`；`else if` 为 `else` + 内层 `if`。条件须为可参与布尔上下文的类型（见 `SemanticBinder`）。 |
| `while ( Cond ) Stmt` | `Parser` 产出 `Stmt.While`。条件与 `if` 相同（布尔上下文）。**循环体**为**新块作用域**：`{ … }` 时与 `Stmt.Block` 一致；非块单语句时语义/运行各加一帧（`SemanticBinder` `scopes.push` / `RuntimeExecutor` `env.push`）。 |
| `break` / `continue` | `Stmt.Break` / `Stmt.Continue`；**无标签**，仅作用于**最近一层** `while`。出现在循环外 → `E_SEM_BREAK_OUTSIDE_LOOP` / `E_SEM_CONTINUE_OUTSIDE_LOOP`（见 [errors.md](errors.md)）。 |
| 空语句 `;` | `Stmt.Nop`。 |
| 既有语句 | `Expression`（**任意 `Expr` + `;`**，不限于调用）、`Return`、`Block`、`VarDecl`、`Assign`（`=` 与复合赋值）、`Update`、`StaticMark` 等，行为见各专题文档。 |

---

## 表达式（解析 + 语义 + 运行）

### 运算符与优先级

与 [`docs/obr/operators.md`](../obr/operators.md) 一致的方向：`Parser` 中自顶向下为 **`parseAssignment`**（`=`/`+=`/…，右结合，低于 `?:`）→ **`?:`** → **`||`** → **`&&`** → **`|`** → **`^`** → **`&`** → **相等** → **关系** → **移位** → **加减** → **乘除模** → **幂 `**`（右结合）→ **一元** → **后缀** → **基本式**。

### 已实现的一类行为

| 类别 | Blink 行为摘要 |
|------|----------------|
| 相等 `==` / `!=` | 静态：两侧类型须一致，且为规范允许的标量/`string`/`char` 等；结果 `boolean`。运行：`string` 为**引用**相等（见 `RuntimeExecutor`）。 |
| 关系 `<` `<=` `>` `>=` | 静态：两侧类型须一致，且为 `byte`/`short`/`int`/`long`；结果 `boolean`。运行：按 `int`/`long` 比较路径（值承载为 `Value` 的整型族）。 |
| 逻辑 `&&` / `||` | 静态：操作数须可用于布尔上下文；结果 `boolean`。运行：**短路**求值。 |
| 三元 `?:` | 静态：条件可用于布尔上下文；**两分支类型须一致**；`:` 后为 **`parseAssignment`**，分支内可有赋值/复合赋值或嵌套 `?:`。运行：先求条件再求所选分支。 |
| 赋值表达式 | `Expr.Assign`；静态/运行规则与 `Stmt.Assign` 一致；可作 `?:` 分支或链式 `a = b = c`。 |
| 算术与 `**` | 与 `NumericExprTyping`、`SemanticBinder#inferArithmeticType` / `#inferPowType` 一致；**`byte` 与除 `byte` 外的其它数值不得在同一二元算术 / `**`（非 byte-byte）中混用**（静态 `E_SEM_TYPE_INFER_EXPR`）。 |
| 位运算与移位（`&`、`|`、`^`、`<<`、`>>`、`>>>`） | 静态结果类型 **`int`**；**`byte` 不得与其它数值混用**（与 [`operators.md`](../obr/operators.md) §7）；运行时 `ToInt32`/`ToUint32`、移位量 mod 32。 |
| 一元 `~` | 操作数为**任意数值 primitive**；静态结果 **`int`**（[`operators.md`](../obr/operators.md) §6.3）；运行时 **`ToInt32`** 后按位取反。 |
| `+` | 数值提升或 **`string`/`char` 与允许侧拼接**（含数字转字符串）；静态规则见 `SemanticBinder#inferAddType`。 |
| 复合赋值 | `string` 仅支持 `+=`；`byte` 左侧复合赋值右侧须为 `byte`；其它见 `SemanticBinder` 赋值分支。 |

### 词法

- `==`、`!=`、`<`、`<=`、`>`、`>=`、`<<`、`>>`、`>>>`、`&`（或 `&&`）、`|`（或 `||`）、`^`、`&&`、`||`、`?`（三元）。
- 关键字 `if`、`else`、`while`、`break`、`continue`。
- **`char`**：`''`（空，与 `'\0'` 同值）、`'\''` 等；`Lexer` + `CharLiteralParser`。
- 单字符 `&` / `|` 与双字符 `&&` / `||` 的区分见 [lexing.md](lexing.md)。

---

## 运行时（节选）

- **整型除零 / 取模除零**：`int` / `long` 的 `/` 与 `%` 在除数为 0 时 → **`E_RT_INTEGER_DIV_ZERO`**（见 [errors.md](errors.md)）。`float`/`double` 按 IEEE 754，**不**抛该码。
- **`if` 与 void 尾调用**：`if` 分支内的语句由 `executeStmtWithoutBlock` 等处理；若分支末条产生 void **尾调用**，仍经 `StmtExecResult.TailVoidCall` 与外层 `call` 循环承接（见 [execution.md](execution.md)）。
- **未初始化 / `undefined`**：`RuntimeExecutor` 中关系运算经 `toLongInteger` 仅接受运行时 `ValueType` 为 **`INT` 或 `LONG`** 的标量；若为 **`UNDEFINED`**（或其它非上述整型标量），抛出 `ObrException`，消息体含 **`关系运算期望整数标量`** 及实际类型名。复合赋值与 `++`/`--` 读值路径若遇 `UNDEFINED` 会走 **`E_RT_COMPOUND_UNINIT`**（见 [errors.md](errors.md)）。程序应通过赋值或初始化保证在参与这些运算前变量已定义（见 [`docs/obr/runtime.md`](../obr/runtime.md)）。

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

## 相对 `docs/obr/` 全集：Blink 仍须单独声明的差异或须知

下列与规范全文对比时**固定成立**（不因表述含糊而依赖「再查源码」）。**位运算与移位**、**一元 `~`**（§6.3：数值 primitive → `int`）已与规范对齐，**不再**列入本表（见上文「已实现的一类行为」）。

| 主题 | Blink 行为 |
|------|------------|
| **`#LINK`** | **`projectRoot`**：`ProjectRootResolver`。**逗号续行**：`LinkParser` + `Lexer`（见 [`lexing.md`](lexing.md)）。**跨文件访问**：`ProgramLinkIndex` + `SemanticBinder`（`E_SEM_LINK_ACCESS_DENIED`）。**扫描**仍全量 walk（不按 `#LINK` 过滤文件）。**`libs/`** 下系统托管实现的 `deRfun` 不受 `#LINK` 限制（与 `system.md` 一致）。见 [project-preproc.md](project-preproc.md)。 |
| **`null` / `undefined` 与 `+`** | 静态：`SemanticBinder` 对 `+` 拼接禁止与 `boolean` / `null` / `undefined` 等混合（见报错消息）。运行时字面量与运算分支以 `SemanticBinder` + `RuntimeExecutor#evalExpr` 为准。 |
| **字符串 `==` / `!=`** | 运行时为 **Java 引用相等**（`left.asString() == right.asString()`），非逐字符值相等。**字面量**每次求值经 **`new String(content)`** 得到独立实例（不驻留），故 **`"" == ""`** 为 **假**（与 [`docs/obr/types.md`](../obr/types.md) 中「可驻留」为可选策略、本引擎不驻留字面量实例一致）。 |

其它专题未单独声明的差异：以本节上文各表、`errors.md` 与 **`Lexer` / `Parser` / `SemanticBinder` / `NumericExprTyping` / `RuntimeExecutor`** 源码为准。
