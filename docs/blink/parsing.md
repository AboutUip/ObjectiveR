# 语法（`com.kitepromiss.obr.parse`）

AST 各节点字段一览见 [ast-reference.md](ast-reference.md)。

**类**：`Parser`（递归下降）、`TokenCursor`（向前看记号序列）。

**输入**：`List<Token>`（由 `Lexer.readAllTokens` 产生）。  
**输出**：`ObrFile` 或 `MrFile`（`ast` 包）。

---

## `TokenCursor`

| 方法 | 行为 |
|------|------|
| `peek()` | 当前记号；若已越过 EOF 则抛 `ObrException`（消息「词法序列越界」） |
| `peekToken(offset)` | 向前看第 `offset` 个记号（`0` 同 `peek`）；越界时落在 `EOF`（见 `parseStmt` 中 `IDENT` + `++`/`--` 判别） |
| `check(k)` | `peek().kind() == k` |
| `advance()` | 返回当前记号并 `pos++` |
| `eof()` | `check(EOF)` |
| `expect(k, fileName)` | 非 `k` 则抛 `file:line:col: 期望 … 实际 …` |

---

## `Parser#parseObrFile`

顶层循环直至 `EOF`，每项为以下之一：

| 当前记号 | 产出 `ObrItem` |
|----------|------------------|
| `PREPROCESSOR_LINE` | `Preproc(rawLine)` |
| `IMPORT` | `parseImport` → `Import` |
| `DE_RFUN` | `parseObrDeRfunDef` → `DeRfunDef` |
| 其它 | `err`（期望预处理、import 或 deRfun） |

`deRfun` 结构：`deRfun` + `QualifiedName` + `(` `ParamList` `)` `:` `TypeRef` + `BlockStmt`。  
`BlockStmt` 由 `parseBlock(fnReturn)` 解析，`fnReturn` 用于 `return` 与 void 判定。

---

## `Parser#parseMrFile`

顶层：`namespace` → `parseMrNamespace`；否则 `parseAttributes` + `deRfun` + `parseMrDeRfunDecl`（分号结尾）。  
`QualifiedName.join(prefixStack, q)` 拼出带命名空间前缀的声明名。

---

## 语句（`.obr` 函数体）

`parseStmt(fnReturn)` 分支（顺序匹配）：

| 前缀 / 形式 | 产出 `Stmt` |
|-------------|-------------|
| `return` | void → `err`；`return;` → `err`；否则 `parseExpr` + `;` → `Return` |
| `if` `(` `parseExpr` `)` `parseStmt` | `If`；可选 `else` + `parseStmt` |
| `;` | `Nop`（空语句） |
| `{` … `}` | `Block`（嵌套 `parseBlock`） |
| `public static` / `private static` | `parseVarDecl`（`PUBLIC_STATIC` / `PRIVATE_STATIC`） |
| `static` | 后跟 `var` → `parseVarDecl(PUBLIC_STATIC)`；否则 `ident` + `;` → `StaticMark` |
| `var` | `parseVarDecl(LOCAL)` |
| `++` / `--` + `ident` + `;` | `Update`（前缀增/减） |
| `ident` + `=` / `+=` / … + `expr` + `;` | `Assign` |
| `ident` + `++` / `--` + `;`（用 `peekToken` 与赋值区分） | `Update`（后缀） |
| 其它表达式起头 | `parseCallExpr` + `;` → `Expression` |

`parseVarDecl`：`var[type] a, b = …;`；同一声明中要么全部带初值要么全部不带。

---

## 表达式

`parseExpr()` 入口为 `parseConditional()`。自顶向下层次（与 [`docs/obr/operators.md`](../obr/operators.md) 优先级一致）：

| 层次 | 方法 | 运算符 / 说明 |
|------|------|----------------|
| 条件 | `parseConditional` | `?:`（右结合：`?` 后 `parseExpr`，`:` 后递归 `parseConditional`） |
| 逻辑或 | `parseLogicalOr` | `||`（左结合） |
| 逻辑与 | `parseLogicalAnd` | `&&`（左结合） |
| 相等 | `parseEquality` | `==` `!=`（左结合） |
| 关系 | `parseRelational` | `<` `<=` `>` `>=`（左结合） |
| 加减 | `parseAdditive` | `+` `-`（左结合） |
| 乘除模 | `parseMultiplicative` | `*` `/` `%`（左结合） |
| 幂 | `parseExponent` | `**`（右结合：右侧递归 `parseExponent`） |
| 一元 | `parseUnary` | 前缀 `++`/`--`（仅 `NameRef`）、`+` `-` `!` `~`，再 `parsePostfix(parsePrimary())` |
| 后缀 | `parsePostfix` | 链式 `++`/`--`（仅 `NameRef`） |
| 基本 | `parsePrimary` | 括号、`Literal`、关键字字面量、`IDENT`（调用或 `NameRef`） |

**`IDENT` 与调用**：先 `parseQualifiedName`；若下一记号是 `(` 则 `parseArgList` → `Expr.Invoke(CallExpr)`；若限定名仅一段则 `NameRef`；多段且非调用 → `err`（限定名须以调用出现）。

---

## `Expr` 变体（`Expr.java`）

`Literal`、`NameRef`、`Invoke`、`Unary`、`Binary`、`Postfix`、`PrefixUpdate`、**`Conditional`**（三元）。

---

## 错误

`Parser#err`：`fileName:line:col: message`，**无统一 `E_*` 前缀**（见 [errors.md](errors.md)）。
