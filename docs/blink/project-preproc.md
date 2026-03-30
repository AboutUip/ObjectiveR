# 工程解析与预处理（Blink 行为）

语言规范中的预处理与项目根规则见 `docs/obr/preprocessor.md`、`docs/obr/runtime.md`。本节描述 **Blink 源码** 中的对应实现。

---

## `ProjectLocator.resolve(Path input)`

| 输入 | 行为 |
|------|------|
| 路径不存在 | `ObrException`（无前缀码），消息含「路径不存在」 |
| 普通文件 | 文件名**必须**为 `main.obr`（大小写敏感）；否则抛错。读入该文件全文，由 `ProjectRootResolver.resolveProjectRoot(mainObr, source)` 结合 `main.obr` 中全部 `#LINK` 行（见 `LinkParser`）确定 **项目根** |
| 目录 | 递归遍历子树，收集名为 `main.obr` 的文件；0 个 → 错误；多于 1 个 → 错误（列出路径）；对唯一 `main` 同上读源码并解析项目根 |
| 非文件非目录 | `ObrException`「不支持的启动路径类型」 |
| `IOException` | 包装为 `ObrException`「无法访问路径」 |

### 项目根（与 `docs/obr/runtime.md` §3 一致）

- **默认**或未列出 `#LINK`：等价 `#LINK /`，项目根 = `main.obr` **所在目录**。
- **合并后的 `#LINK` 列表**（多行 `#LINK` 并集）中若存在规范化后的文件项 **`/main/main.obr`**：项目根 = `main.obr` 路径的**上两级**父目录，且须满足磁盘路径与 `<项目根>/main/main.obr` 规范化后一致，否则 → `E_LINK_ROOT_MISMATCH`（`ProjectRootResolver`）。

---

## `ObrProgramLoader.loadAllObr`

- 起点：`projectRoot`（绝对规范化路径，已由 `ProjectLocator` + `ProjectRootResolver` 确定）。
- 将已解析的 `mainPath` + `mainAst` 作为第一条 `ParsedObrFile`。
- `Files.walkFileTree`：对每个常规文件，扩展名为 `.obr` 且路径不等于 `main` 的，读入 → `Lexer.readAllTokens` → `Parser.parseObrFile` → 追加。
- `preVisitDirectory`：目录名为 `.git`、`target`、`build`、`.idea`、`node_modules` 时 **SKIP_SUBTREE**。

因此：**所有**可 walk 到的 `.obr`（除上述跳过树）都会进入同一 `ObrProgramBundle`（与 `docs/obr/runtime.md` §4.1 全量扫描一致）。**不在**此阶段按 `#LINK` 排除文件；**跨文件符号访问**是否合法由 `ProgramLinkIndex` + `SemanticBinder` 按各文件的 `#LINK` 判定（见下）。

---

## 预处理行 `ObrItem.Preproc`

| 环节 | 行为 |
|------|------|
| 词法 | `Lexer` 将 `#` 起始的**整行**（无换行）读为 `PREPROCESSOR_LINE` |
| 语法 | `Parser.parseObrFile` 顶层遇到该记号 → `new ObrItem.Preproc(lexeme)` |
| 语义 | **`VersionDirectiveChecker`** 仅识别符合正则的 `#VERSION <正整数>`（见 [version-directive.md](version-directive.md)）；同文件多条须一致且等于 `ObrLanguageVersion.SUPPORTED` |
| **`#LINK`** | **`LinkParser`** 自源码合并各 `#LINK` **指令**（含逗号后换行续写，与规范 §4 一致）；`Lexer` 对 `#LINK` 续行产出**单条** `PREPROCESSOR_LINE`。**`ProgramLinkIndex.from`** 为每个 `.obr` 建立列表（无 `#LINK` 时等价 `["/"]`）。**`SemanticBinder`** 在跨文件调用 `deRfun` 实现或解析**跨文件 `public static`** 时，要求调用方路径命中被引用文件所声明的 `#LINK` 列表（**同文件**与 **`libs/` 下系统托管实现**除外）；违反 → `E_SEM_LINK_ACCESS_DENIED`。 |

---

## 相关文档

- 管线位置：[pipeline.md](pipeline.md) 步骤 1、6。
- 模块扫描与 `libs/`：[modules.md](modules.md)。
- 规范边界：[implementation-scope.md](implementation-scope.md)。
