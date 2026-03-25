# 工程解析与预处理（Blink 行为）

语言规范中的预处理与项目根规则见 `docs/obr/preprocessor.md`、`docs/obr/runtime.md`。本节只描述 **Blink 源码实际怎么做**，便于与规范对照。

---

## `ProjectLocator.resolve(Path input)`

| 输入 | 行为 |
|------|------|
| 路径不存在 | `ObrException`（无前缀码），消息含「路径不存在」 |
| 普通文件 | 文件名**必须**为 `main.obr`（大小写敏感）；否则抛错。`ProjectResolution(mainObr, parent)`，**项目根** = `main.obr` **所在目录** |
| 目录 | 递归遍历子树，收集名为 `main.obr` 的文件；0 个 → 错误；多于 1 个 → 错误（列出路径）；否则同上取父目录为项目根 |
| 非文件非目录 | `ObrException`「不支持的启动路径类型」 |
| `IOException` | 包装为 `ObrException`「无法访问路径」 |

**与语言规范差异（须牢记）**：`docs/obr/runtime.md` 中由 `#LINK` 语言空间推导项目根（例如入口映射为 `/main/main.obr` 时根为上一层）的规则，**当前 Blink 未实现**。解释器**从不**根据 `#LINK` 改写文件系统上的 `projectRoot`；`projectRoot` **始终**为磁盘上定位到的 `main.obr` 的父目录。`#LINK` 行仍保留在 AST 的 `ObrItem.Preproc` 中，但**不参与**路径计算。若将来实现规范，应改 `ProjectLocator` 或在其后增加一步「按 `main.obr` 预处理解析根」。

`ProjectResolution` 的 JavaDoc 中「默认 `#LINK /` 时根为 `main.obr` 所在目录」描述的是**与规范对齐的特例**；在 Blink 中即**当前唯一行为**。

---

## `ObrProgramLoader.loadAllObr`

- 起点：`projectRoot`（绝对规范化路径）。
- 将已解析的 `mainPath` + `mainAst` 作为第一条 `ParsedObrFile`。
- `Files.walkFileTree`：对每个常规文件，扩展名为 `.obr` 且路径不等于 `main` 的，读入 → `Lexer.readAllTokens` → `Parser.parseObrFile` → 追加。
- `preVisitDirectory`：目录名为 `.git`、`target`、`build`、`.idea`、`node_modules` 时 **SKIP_SUBTREE**。

因此：**所有**可 walk 到的 `.obr`（除上述跳过树）都会进入同一 `ObrProgramBundle`，与 `main.obr` 的 `#LINK` 声明无关。

---

## 预处理行 `ObrItem.Preproc`

| 环节 | 行为 |
|------|------|
| 词法 | `Lexer` 将 `#` 起始的**整行**（无换行）读为 `PREPROCESSOR_LINE` |
| 语法 | `Parser.parseObrFile` 顶层遇到该记号 → `new ObrItem.Preproc(lexeme)` |
| 语义 | **`VersionDirectiveChecker`** 仅识别符合正则的 `#VERSION <正整数>`（见 [version-directive.md](version-directive.md)）；同文件多条须一致且等于 `ObrLanguageVersion.SUPPORTED` |
| 其它以 `#` 开头的行 | **保留在 AST 中**，当前**无**其它解释器阶段消费（包括 `#LINK`） |

---

## 相关文档

- 管线位置：[pipeline.md](pipeline.md) 步骤 1、6。
- 模块扫描与 `libs/`：[modules.md](modules.md)。
- 规范边界：[implementation-scope.md](implementation-scope.md)。
