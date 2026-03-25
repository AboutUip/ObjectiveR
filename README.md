<a id="top"></a>
<div align="center">

<img src="docs/assets/obr-logo.svg" alt="Obr logo" width="96" height="96"/>

# ObjectiveR

**Obr** — 解释型、强类型语言 · 源文件 **`.obr`**

[BlinkEngine](docs/blink/README.md) · Java 参考解释器（`com.kitepromiss.obr.ObrInterpreter`）

<br/>

[**语言规范**](docs/obr/README.md) · [**BlinkEngine**](docs/blink/README.md) · [**docs 导航**](docs/README.md) · [**编辑器扩展**](Expansion/README.md)

</div>

---

## 概览

| | |
|:---|:---|
| **语言** | ObjectiveR（简称 **Obr**） |
| **实现** | **BlinkEngine**（语言名不变；*Obr* = 语言，*BlinkEngine* = 本仓库解释器） |
| **类型** | 强类型 · 标识符 **大小写敏感** |
| **运行** | 解释执行 · 目标 JVM |

> **版本**：各语言版本 **互不兼容**；升级请以对应版本文档与工具链为准，勿混用语法与运行时行为。

---

## 文档地图

| 入口 | 说明 |
|:--|:--|
| [**`docs/README.md`**](docs/README.md) | 文档总导航（`docs/` 内快速链接） |
| [**`docs/obr/README.md`**](docs/obr/README.md) | **Obr** 语言规范 — 专题索引 |
| [**`docs/obr/overview.md`**](docs/obr/overview.md) | 规范总览与分层 |
| [**`docs/blink/README.md`**](docs/blink/README.md) | **BlinkEngine** 实现 — 管线、追溯、体例 |
| [**`docs/blink/architecture.md`**](docs/blink/architecture.md) | 子包、类与数据流 |

<details>
<summary><strong>BlinkEngine 专题（点击展开）</strong></summary>

| 文档 | 内容 |
|:--|:--|
| [`logic-index.md`](docs/blink/logic-index.md) | **全逻辑速查**（主题→文档→源码） |
| [`pipeline.md`](docs/blink/pipeline.md) | `ObrInterpreter#run`、CLI、`finally` |
| [`project-preproc.md`](docs/blink/project-preproc.md) | 工程根、扫描、`#LINK` 与规范差异 |
| [`lexing.md`](docs/blink/lexing.md) / [`parsing.md`](docs/blink/parsing.md) | 词法 / 语法 |
| [`ast-reference.md`](docs/blink/ast-reference.md) | AST 节点与枚举字段表 |
| [`modules.md`](docs/blink/modules.md) | 项目路径、多文件、`.mr`、`LibsProvisioner` |
| [`version-directive.md`](docs/blink/version-directive.md) | `#VERSION` |
| [`semantic-binding.md`](docs/blink/semantic-binding.md) | 语义、`FileStaticRegistry` |
| [`overload-resolution.md`](docs/blink/overload-resolution.md) | `NumericWidening` |
| [`execution.md`](docs/blink/execution.md) | 运行时、`std::rout`、void 尾调用（TCO）、静态槽 |
| [`runtime-model.md`](docs/blink/runtime-model.md) | `Value`/`Env`/栈等内部模型 |
| [`errors.md`](docs/blink/errors.md) | `E_*` 前缀码 |
| [`testing.md`](docs/blink/testing.md) | 单元测试类索引 |
| [`audit.md`](docs/blink/audit.md) | 审计 `phase` |
| [`implementation-scope.md`](docs/blink/implementation-scope.md) | 相对全集的实现边界 |
| [`supporting.md`](docs/blink/supporting.md) / [`inventory.md`](docs/blink/inventory.md) | 支撑类型 · 源码↔文档对照 |

</details>

| 其它 | 说明 |
|:--|:--|
| [`docs/obr/runtime.md`](docs/obr/runtime.md) | 执行模型、调用栈与实现注记（§5.2） |
| [`docs/obr/system.md`](docs/obr/system.md) | `system.mr`、`std::rout`、`libs/` |
| [`Expansion/README.md`](Expansion/README.md) | VS Code / Cursor 扩展（高亮、图标、打包） |

---

## 构建与产物

解释器仅需 **JDK**；测试依赖见 `pom.xml`（`junit-jupiter` 为 `test` 范围，不打入发行包）。

**推荐（`products/` 脚本）**

| 脚本 | 说明 |
|:--|:--|
| [`products/build-jar.ps1`](products/build-jar.ps1) | Fat **JAR**（可加 `-SkipTests`） |
| [`products/build-win-exe.ps1`](products/build-win-exe.ps1) | **Windows**：含 `BlinkEngine.exe` 的应用目录（可加 `-SkipTests`） |

产出默认在 **`products/out/`**。细节见 [`products/README-build.md`](products/README-build.md)。

**Maven**

| 命令 | 产物 |
|:--|:--|
| `mvn package` | `target/BlinkEngine.jar`（shade 可执行）；`java -jar products/out/BlinkEngine.jar` |
| `mvn package -Pwin-exe`（Windows，需 **jpackage**） | `products/out/` 下 Windows 应用布局；脚本可展平为单层 `BlinkEngine.exe` |

---

## 仓库布局（摘要）

```text
docs/obr/      ← 语言规范（专题）
docs/blink/    ← BlinkEngine 实现说明
src/main/java/ ← 解释器源码（com.kitepromiss.obr）
Expansion/     ← 编辑器扩展（独立打包）
products/      ← 构建脚本与产物说明
```

---

## 设计取向（简述）

在多范式之间折中：**面向对象**组织大型结构、**过程式**控制流、**函数式**与**泛型**表达复用（详见 [`docs/obr/overview.md`](docs/obr/overview.md)）。

---

## 协议

<p align="center">
  <a href="LICENSE"><img src="docs/assets/mit-badge.svg" alt="MIT License" height="28"/></a>
</p>

以 [**MIT License**](LICENSE) 发布；版权见许可证全文。

---

<div align="center"><sub><a href="#top">↑ 返回顶部</a></sub></div>
