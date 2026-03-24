# ObjectiveR 规范总览

<div align="center"><img src="../assets/obr-logo.svg" alt="Obr" width="56" height="56"/></div>

**导航**：[规范索引](README.md) · [docs 说明](../README.md)

## 规范文档状态

本目录 `obr/`（语言专题规范）的条文具有**规范级**效力；本条说明文档自身的成熟度标签，**不**替代语言版本（`#VERSION`，见 [`preprocessor.md`](preprocessor.md)）。

### 术语

| 标签 | 含义 |
|------|------|
| **草案（Draft）** | 规范仍可增删改；允许与参考解释器存在未对齐或待定条款；读者不得将条文视为不可变更的对外契约。 |
| **稳定（Stable）** | 针对某一 **Obr 语言版本**（见下），`obr/` 专题中适用于该版本的条文视为**冻结**：后续修改须通过**新版本语言**、**修正案**或维护者明示的修订流程，不得默示兼容。 |

### 当前结论

- **当前标签**：**草案（Draft）**。
- **适用范围**：ObjectiveR 解释器及其语言版本（与 [`preprocessor.md`](preprocessor.md) 中 `#VERSION` 一致）。
- **当前基准（草案阶段的参照）**：
  - **Obr 语言版本 `1`**（规范与解释器当前唯一已定义并实现的语言版本）。
  - **参考实现**：本仓库中的 **BlinkEngine**（`com.kitepromiss` / 构件 `ObjectionR`，构建坐标以根目录 `pom.xml` 为准）。草案阶段以**仓库内当前实现与 `docs/obr/` 同级内容**为事实来源，**不**绑定某一 Maven 发布号或 Git 标签为「规范版本号」。

### 升格为「稳定（Stable）」的条件

当且仅当维护者确认下列**全部**成立时，可将本文「当前标签」更新为 **Stable**，并在同节**追加**一行 **Stable 记录**（生效日期、适用的 Obr 语言版本、参考解释器发布标识）：

1. **语言版本范围**：明确 Stable 绑定于 **哪一个 Obr 语言版本**（当前语境下仅可能为 **`1`**）；该版本在 `preprocessor.md` 与实现中已一致定义。
2. **与参考实现对齐**：存在一次**正式发布**的参考解释器（**不得**仅为 `*-SNAPSHOT` 或无名本地构建），且维护者声明：对绑定语言版本而言，[`../blink/implementation-scope.md`](../blink/implementation-scope.md) 与 `docs/obr/` 中适用于该版本的条文之间**无已知冲突**；有意保留的差异须在实现说明或发布说明中**显式列出**。
3. **可指明的发布物**：Stable 记录中须写明参考解释器的**可复现标识**（例如：**已发布的** Maven 坐标与版本号，和/或 **Git 标签**）。读者应能据此取得与 Stable 声明一致的解释器二进制或构建来源。

在未满足上述条件前，规范保持 **Draft**；实现与文档的演进不改变本条对 Stable 的定义。

## 基础约定

- 源文件扩展名为 `.obr`
- 语言为解释执行
- 解释器由 Java 实现（原生支持跨平台运行）
- 语言为强类型
- 语言采用严格大小写检查（Case-Sensitive）
- 各版本之间默认互不兼容

## 版本策略

- 不同版本支持的语法与预处理指令集合是确定的。
- 解释器应仅接受其支持版本的语言特性。
- 文件若未显式声明版本，默认按当前解释器可解释执行的 Obr 版本处理。
- **语言版本号**为单调递增的非负整数（见 [`preprocessor.md`](preprocessor.md) 中 `#VERSION`）。当前仅定义并实现版本 **`1`**；显式写出不支持的版本号必须报错。

## 规范与语言实现

- **`docs/obr/`（本文及专题）**：描述 ObjectiveR **目标语言**的规则与判定，不逐项标注「解释器是否已实现」。
- **当前参考实现 BlinkEngine 的实际支持范围**：见 [`../blink/README.md`](../blink/README.md)、[`../blink/implementation-scope.md`](../blink/implementation-scope.md)（实现说明，非规范正文）。

## 规范分层

以下链接指向本目录内专题文档（相对路径，便于在仓库内跳转）。

| 主题 | 文档 |
|------|------|
| 预处理指令 | [preprocessor.md](preprocessor.md) |
| 执行模型（入口、资源、`main`） | [runtime.md](runtime.md) |
| 系统头与 `libs/` | [system.md](system.md) |
| 可见性 | [visibility.md](visibility.md) |
| `static` | [static.md](static.md) |
| 语句终止与换行 | [termination.md](termination.md) |
| moduleR（`.mr`、`import`） | [moduleR.md](moduleR.md) |
| 注释 | [comments.md](comments.md) |
| 基础数据类型 | [types.md](types.md) |
| 作用域与标识符 | [scope.md](scope.md) |
| 运算符 | [operators.md](operators.md) |

## 已确认约束

- 项目空间由 `main.obr` 的语言空间唯一决定，与启动目录无关。
