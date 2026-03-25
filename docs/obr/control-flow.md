# ObjectiveR 条件分支与条件表达式

本文规定 `if` / `else`、`else if` 链、以及条件运算符 `?:` 的语法与语义；**条件表达式在运行时的真值**统一见 [`operators.md`](operators.md) §1.1（`ToBoolean`）。`while` / `for` 等循环不在本文范围。

---

## 1. `if` 与 `else`

### 1.1 语法（文法意图）

设 `Cond` 为括号内的**条件表达式**，`Stmt` 为**单条语句**，`Block` 为 `{` … `}` **块语句**（块内为语句序列，规则同现有块作用域，见 [`scope.md`](scope.md)）。

允许的形如下列（`else if` 视为 `else` 后紧跟一个 `if` 语句，见 §1.3）：

| 形式 | 说明 |
|------|------|
| `if ( Cond ) Stmt` | 条件为真时执行 `Stmt`。`Stmt` 可为**空语句**（仅 `;`），见 §1.5。 |
| `if ( Cond ) Stmt ;`（非块形式） | **无花括号**时，`Stmt` 须按 [`termination.md`](termination.md) 终止；**空语句**写为 `if ( Cond ) ;` |
| `if ( Cond ) Block` | 条件为真时执行块；`}` **不**要求后跟 `;`（与现有块语句一致）；若 `}` 后紧跟 `else`，不得插入会切断 `else` 绑定的记号 |
| `if ( Cond ) Block else Block` | 二选一 |
| `if ( Cond ) Block else if ( Cond ) … else Block` | `else if` 链：见 §1.3 |

**禁止**：`if ( Cond )` 后**既无**合法 `Stmt` **也无** `Block`（例如 `if ( Cond )` 直接换行或文件结束，或紧跟非语句起点的记号且无法解析为 `Stmt`）。**允许** `if ( Cond ) ;`（空语句）。

### 1.2 条件表达式的类型与 `ToBoolean`

- **语义要求**：进入分支判定前，必须将 `Cond` 的**值**转为 `boolean` 真值（真 / 假）。
- **静态类型**：`Cond` 的**静态类型**可以是 `boolean`，或允许出现在**布尔上下文**的类型（见 [`operators.md`](operators.md) §1.1）；若某类型**禁止**出现在布尔上下文，则必须**静态报错**。
- **隐式转换**：当 `Cond` 的静态类型**不是** `boolean` 时，必须按 [`operators.md`](operators.md) §1.1 **`ToBoolean`** 做**隐性**转换后再分支；**不得**对禁止类型静默成功。

### 1.3 `else if` 链

- `else if ( Cond ) StmtOrBlock` 等价于：`else` 后接**单个** `if` 语句（该 `if` 可再带自己的 `else`）。
- **`else` 绑定**：`else` **始终**与**同一嵌套层级上、此前最近、且尚未被其它 `else` 占用的** `if` 配对（**就近原则**，消除悬挂 `else` 歧义）。

### 1.4 与语句终止、换行

- `if ( Cond ) Stmt` 形式中，单条 `Stmt` 的终止规则不变（见 [`termination.md`](termination.md)）。
- `if ( Cond ) { … }` 以闭合 `}` 结束该 `if` 语句整体；其后若接 `else`，**不得**在 `}` 与 `else` 之间插入会改变解析的记号；不要求 `}` 后必须写 `;`（与「块作为整条语句」的惯例一致）。

### 1.5 空语句与 `if`

- **空语句**（仅 `;`）是合法语句：不执行任何操作。
- **`if ( Cond ) ;`**：合法；条件为真时执行体为空语句（与「什么都不做」一致）。
- **`if ( Cond ) { }`**：合法；条件为真时执行空块。
- **控制流**：对任意 `Cond`，**`if ( Cond ) ;` 与 `if ( Cond ) { }` 语义等价**——条件为真时均不执行有效语句体；条件为假时均不执行 then 分支。二者与 `else` 的配对、与后续语句的边界解析规则仍按 §1.3、§1.4。
- **无花括号**的 then 分支**必须**以一条完整 `Stmt`（含空语句 `;`）结束，以满足 [`termination.md`](termination.md) 的终止规则。

---

## 2. 逻辑与关系、相等（在条件中的使用）

在 `Cond` 中允许使用下列运算符（完整优先级与关系/相等的类型约束见 [`operators.md`](operators.md) §2、§8）：

- **一元**：`!`
- **逻辑**：`&&`、`||`（短路；结果类型为 `boolean`，见 `operators.md` §4）
- **关系**：`<`、`<=`、`>`、`>=`（**仅** `byte`/`short`/`int`/`long` 同型；**无**隐性转换，见 `operators.md` §8）
- **相等**：`==`、`!=`（**严格**：无隐性转换；允许类型见 `operators.md` §2）

上述表达式的**结果类型**为 `boolean` 时，**不再**二次 `ToBoolean`（已为布尔）。

---

## 3. 条件运算符 `?:`（三元）

### 3.1 语法

- 形式：`CondQ ? Expr1 : Expr2`
- `CondQ` 的布尔语义与 `if ( Cond )` 相同：须先对 `CondQ` 做 **`ToBoolean`**（见 `operators.md` §1.1），再选择分支。
- **结合性**：与 JavaScript 一致，**右结合**（`a ? b : c ? d : e` 解析为 `a ? b : (c ? d : e)`）。

### 3.2 类型与结果

- **`Expr1` / `Expr2`**：须为合法表达式；**不得**为无值的 `void` 调用作为分支值（若某侧为 `void` 调用，须静态报错，与现有「void 不得作表达式值」一致）。
- **结果类型**：`Expr1` 与 `Expr2` 的**静态类型须完全一致**；表达式结果类型即该类型。
- **说明**：与 **`+`（`operators.md` §3）、关系/相等（§2、§8）** 条文正交；不得用「一侧为 `string`」等规则放宽「两分支同型」要求。

### 3.3 优先级

- 低于 `||`，高于赋值（与 JavaScript 一致）；完整表见 [`operators.md`](operators.md) §9。

---

## 4. `char` / `string` / 数值与 `ToBoolean`（与 `if` 条件）

- **数值**：**`0`**（及浮点 **正负零**）为 **假**，与 [`operators.md`](operators.md) §1.1 一致。
- **`string`**：**`""`** 为 **假**；非空串为 **真**。
- **`char`**：**`''`**（空字面量）与 **`'\0'`**（同值，码点 0）为 **假**；任一其它单字符为 **真**。
- **`undefined` 与 `null`**：经 `ToBoolean` **均为假**（`false`），见 [`operators.md`](operators.md) §1.1。
- 该规则与 [`operators.md`](operators.md) §1.1、[`types.md`](types.md) §2.2 一致，并适用于 `if`/`?:` 的条件及 `!`、`&&`、`||` 的布尔上下文。

---

## 5. 与实现对照

当前解释器是否已实现 `if` / `else` / `?:` 及全量比较、逻辑运算符，以 [`docs/blink/implementation-scope.md`](../blink/implementation-scope.md) 为准；**本文档为规范目标**，不因实现滞后而失效。
