# AST 参考（`com.kitepromiss.obr.ast`）

与源码 **sealed interface / record** 一一对应，便于从语法树反查字段含义。构造与解析规则见 [parsing.md](parsing.md)。

---

## 顶层

| 类型 | 字段 / 变体 | 说明 |
|------|-------------|------|
| `ObrFile` | `List<ObrItem> items` | 单个 `.obr` 文件根 |
| `MrFile` | `List<MrItem> items` | 单个 `.mr` 文件根 |

---

## `ObrItem`（`.obr` 顶层项）

| 变体 | 字段 | 说明 |
|------|------|------|
| `Preproc` | `String rawLine` | 整行预处理文本（含起始 `#`，与词法 `PREPROCESSOR_LINE` 一致） |
| `Import` | `String moduleName` | `import` 的模块名（无 `.mr` 后缀） |
| `DeRfunDef` | `QualifiedName name`, `List<ParamDecl> params`, `TypeRef returnType`, `BlockStmt body` | 函数定义 |

---

## `MrItem`（`.mr` 顶层 / 命名空间成员）

| 变体 | 字段 | 说明 |
|------|------|------|
| `DeRfunDecl` | `List<Attribute> attributes`, `QualifiedName name`, `List<ParamDecl> params`, `TypeRef returnType` | 仅声明；`name` 已带命名空间前缀（如 `std::rout`） |
| `Namespace` | `String name`, `List<MrItem> members` | `namespace id { ... }`，可嵌套 |

---

## `Stmt`（`.obr` 函数体内语句）

| 变体 | 字段 | 说明 |
|------|------|------|
| `Expression` | `CallExpr call` | 单独调用表达式语句 |
| `Return` | `Expr value` | 必有表达式；无 `return;` 形式 |
| `VarDecl` | `VarVisibility visibility`, `TypeRef type`, `List<VarDeclarator> declarators` | `var` / `static var` / `public|private static var` |
| `Block` | `BlockStmt body` | 嵌套块 |
| `Assign` | `String name`, `AssignOp op`, `Expr value` | 简单赋值或复合赋值；左侧为单标识符 |
| `Update` | `String name`, `UpdateKind kind` | `++`/`--` 语句形式 |
| `StaticMark` | `String name` | `static ident;`，将已有局部标为 static |
| **`If`** | `Expr cond`, `Stmt thenStmt`, `Stmt elseStmtOrNull` | `if ( Cond ) …`；可选 `else`（含 `else if` 链） |
| **`Nop`** | （无字段） | 空语句 `;` |

### `Stmt.AssignOp`

`ASSIGN`, `ADD_ASSIGN`, `SUB_ASSIGN`, `MUL_ASSIGN`, `DIV_ASSIGN`, `MOD_ASSIGN`。

### `Stmt.UpdateKind`

`PREFIX_INCR`, `PREFIX_DECR`, `POSTFIX_INCR`, `POSTFIX_DECR`（运行时效果见 [execution.md](execution.md)）。

---

## `Expr`

| 变体 | 字段 | 说明 |
|------|------|------|
| `Literal` | `String lexeme` | 数字 / 字符串 / `true`/`false`/`null`/`undefined` / `char` 等原始词面 |
| `NameRef` | `String name` | 标识符求值 |
| `Invoke` | `CallExpr call` | 作为表达式的调用 |
| `Binary` | `Expr left`, `BinaryOp op`, `Expr right` | 算术、比较、相等、逻辑、`+` 拼接等 |
| **`Conditional`** | `Expr cond`, `Expr thenExpr`, `Expr elseExpr` | 三元 `?:` |
| `Unary` | `UnaryOp op`, `Expr operand` | 一元 `+ - ! ~` |
| `Postfix` | `Expr operand`, `PostfixOp op` | 后缀 `++`/`--`，求值为旧值 |
| `PrefixUpdate` | `Expr operand`, `PostfixOp op` | 前缀 `++`/`--`，求值为新值 |

### `Expr.BinaryOp`

`ADD`, `SUB`, `MUL`, `DIV`, `MOD`, `POW`（`**`），**`EQ`/`NE`/`LT`/`LE`/`GT`/`GE`**，**`AND`/`OR`**（`&&`/`||`）。静态结果类型见 [semantic-binding.md](semantic-binding.md)。

### `Expr.PostfixOp`

`INCR`, `DECR`。

### `Expr.UnaryOp`

`POS`, `NEG`, `LNOT`, `BITNOT`。

---

## 共用结构

| 类型 | 字段 / 方法 | 说明 |
|------|-------------|------|
| `BlockStmt` | `List<Stmt> statements` | 块内语句序列 |
| `CallExpr` | `QualifiedName callee`, `List<Expr> arguments` | 调用目标与实参 |
| `QualifiedName` | `List<String> segments` | `::` 分段；`of`、`join` 工厂见源码 |
| `TypeRef` | `String keywordLexeme` | 类型关键字词面 |
| `ParamDecl` | `TypeRef type`, `String name` | 形参 |
| `Attribute` | `String name`, `String rawInsideParens` | 如 `@Overwrite(...)` 内原文 |
| `VarDeclarator` | `String name`, `Expr initOrNull` | `var` 列表中的一项 |
| `VarVisibility` | 枚举 | `LOCAL`, `PUBLIC_STATIC`, `PRIVATE_STATIC`；见源码注释 |

---

## 与解析器的对应

- 顶层与语句、表达式的分支顺序：[parsing.md](parsing.md)。
- 词法记号：[lexing.md](lexing.md)、`TokenKind`。
