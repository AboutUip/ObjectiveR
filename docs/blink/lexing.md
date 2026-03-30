# 词法（`com.kitepromiss.obr.lex`）

**实现类**：`Lexer`  
**记号类型**：`TokenKind`（`Token` 为 record：kind、lexeme、行、列）  
**`.obr` / `.mr`**：同一词法器；文件种类由 `Parser#parseObrFile` / `parseMrFile` 区分。

---

## 入口

| 方法 | 行为 |
|------|------|
| `Lexer.readAllTokens(source, fileName, audit)` | 构造 `Lexer`，循环 `nextToken()` 直至 `EOF`，返回不可变列表 |

---

## 扫描模型

- 输入：`String source`、`int pos`，以及 `line`、`column`。
- 前进：`peek()` / `peekAhead(n)` 不移动；`advance()` 移动 `pos` 并更新行列（`\n` 换行；`\r` 可与 `\n` 组合处理）。

---

## `nextToken()` 循环顺序

1. `skipWhitespace()`：跳过空格与制表；遇换行调用 `handleNewline()` 且置 **`lineBegin = true`**。  
   - **规则**：行首缩进空格**不**清除 `lineBegin`，以便识别行前带空白的 `#` 预处理行。
2. 若 `pos >= len` → `EOF`。
3. 若 **`lineBegin` 且当前字符为 `#`** → `readPreprocessorLine()`：从 `#` 扫到换行前（不含换行）。若 `lexeme`（去行首空白后）以 **`#LINK`** 开头且满足「逗号续写」（`LinkParser.endsWithLinkCommaContinuation`：去尾部空白后以 `,` 结尾），则继续读后续行（续行可有行首空格），合并为**一条**逻辑 `PREPROCESSOR_LINE`（与 [`docs/obr/preprocessor.md`](../obr/preprocessor.md) §4 一致）；遇仅空白或空行则停止续行。否则单行为止。产出 **`PREPROCESSOR_LINE`**，`lexeme` 含 `#`。随后 **`lineBegin = true`**（与行首语义一致）。
4. 若 `//` → `skipLineComment()`，继续循环（不产生 token）。
5. 若 `/*` → `skipBlockComment()`；可跨行；未遇 `*/` 则抛 `ObrException`（消息前缀为 `file:line:col:`，**无 `E_*` 码**）。
6. 否则进入记号：`markTokenStart()` 记录本 token 起始行列，随后：
   - 标识符起始 `[a-zA-Z_]` → `readIdentifierOrKeyword()`
   - 数字 → `readNumber()`
   - `"` → `readString()`
   - `'` → `readCharLiteral()`；`''` 与 `'\''` 等由 `CharLiteralParser.parseCharLexeme` 校验（`''` ≡ `'\0'`）
   - `@` → `AT`
   - 单字符界符与运算符：`{ } ( ) [ ] ; , + - % ~ ^ . /` 等；`+` 再看 `+` → `PLUS_PLUS` 或 `=` → `PLUS_EQ` 或 `PLUS`；`-` 同理 → `MINUS_MINUS` / `MINUS_EQ` / `MINUS`；`*` 再看 `*` → `STAR_STAR` 或 `=` → `STAR_EQ` 或 `STAR`；`/`、`%` 再看 `=` → `SLASH_EQ` / `PERCENT_EQ` 或单字符；`:` 再看 `:` → `DOUBLE_COLON` 或 `COLON`；**`=` 再看 `=` → `EQ_EQ`**，否则 **`EQ`**；**`<` 再看 `=` → `LE`**，再看 `<` → **`LT_LT`**，否则 **`LT`**；**`>` 再看 `=` → `GE`**，再看 `>` 时若再 `>` → **`GT_GT_GT`**，否则 **`GT_GT`**，否则 **`GT`**；**`!` 再看 `=` → `NE`**；**`&` 再看 `&` → `AND_AND`**，否则 **`AMP`**；**`|` 再看 `|` → `OR_OR`**，否则 **`PIPE`**；**`^` → `CARET`**；**`?` → `QUESTION`**
   - 其它 → `ObrException`（无法识别字符，含 Unicode 码点）

---

## 关键字

静态表 `KEYWORDS: Map<String, TokenKind>`（`Lexer` 静态块）：`readIdentifierOrKeyword` 读完连续 `[a-zA-Z0-9_]` 后查表；命中则为对应关键字，否则 **`IDENT`**。

`KEYWORDS` 未命中则为 `IDENT`。`var`（`VAR`）用于 `var[type] …` / `static var[type] …`。

### `TokenKind`（与 `TokenKind.java` 一致）

`EOF`、`IDENT`、`NUMBER`、`STRING_LITERAL`、`CHAR_LITERAL`、`PREPROCESSOR_LINE`；界符 `LBRACE`…；比较与逻辑 **`EQ_EQ`、`NE`、`LT`、`LE`、`GT`、`GE`、`LT_LT`、`GT_GT`、`GT_GT_GT`、`AMP`、`PIPE`、`CARET`、`AND_AND`、`OR_OR`、`QUESTION`**；赋值 `EQ`；复合 `PLUS_PLUS`、`PLUS_EQ`、`MINUS_MINUS`、`MINUS_EQ`、`STAR_STAR`、`STAR_EQ`、`SLASH_EQ`、`PERCENT_EQ`、`DOUBLE_COLON`；关键字含 **`IF`、`ELSE`、`WHILE`、`BREAK`、`CONTINUE`** 及 `PUBLIC`、`PRIVATE`、`STATIC`、`TRUE`、`FALSE`、`NULL`、`UNDEFINED`、`BYTE`…`BOOLEAN`、`VOID`、`DE_RFUN`、`IMPORT`、`NAMESPACE`、`RETURN`、`VAR`。

---

## 字面量

| 种类 | 产出 | 说明 |
|------|------|------|
| 数字 | `NUMBER` | 整数段；可选 `.` + 小数段；可选后缀 `f/F`、`d/D`、`l/L`；lexeme 为原文 |
| 字符串 | `STRING_LITERAL` | 从 `"` 到下一个 `"`，**不允许**未转义换行；lexeme **含**两侧引号 |
| 字符 | `CHAR_LITERAL` | 从 `'` 到 `'`；lexeme 含引号；`CharLiteralParser` 校验 |

---

## 注释

- **不产生 token**；`//` 至行尾；`/*` 至 `*/`（块注释内换行更新行号）。

---

## 审计

每个经 `emit()` 的 token 在 **`TraceLevel.VERBOSE`** 下记录 `phase=token`（见 [audit.md](audit.md)）。

---

## 与语法阶段的关系

`Parser` 消费 `List<Token>`；`TokenCursor` 对 `expect` 失败时抛 `ObrException`（见 [errors.md](errors.md) 无前缀一节）。语法规则见 [parsing.md](parsing.md)。
