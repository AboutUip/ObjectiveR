<div align="center">

<img src="assets/obr-logo-wide.svg" alt="Obr" width="200" height="64"/>

# 技术文档 · `docs/`

**Obr** 语言规范 · **BlinkEngine** 实现说明

<br/>

[`obr/`](obr/README.md) · [`blink/`](blink/README.md) · [仓库根 `README`](../README.md)

</div>

---

## 目录

| 路径 | 内容 |
|:--|:--|
| **[`obr/`](obr/README.md)** | ObjectiveR 语言规范（专题拆分） |
| **[`blink/`](blink/README.md)** | BlinkEngine Java 实现（管线、追溯、错误码、源码对照） |
| **[`assets/`](assets/README.md)** | 徽标与矢量资源（`obr-logo.svg` 等，README/文档直接引用） |

**编辑器扩展**（与解释器分仓打包）：[`Expansion/README.md`](../Expansion/README.md)

**试跑示例**：仓库根 [`demo/`](../demo/)（`main.obr`、`test.mr`；行为以解释器与 [blink/implementation-scope.md](blink/implementation-scope.md) 为准）。

---

## 快速链接

### 语言（`docs/obr/`）

| | |
|:--|:--|
| [规范索引](obr/README.md) | 全部专题列表 |
| [总览](obr/overview.md) | 范围、版本、分层 |
| [执行模型](obr/runtime.md) | `main.obr`、根目录、`libs/`、`static var`、调用栈（§5.2） |
| [系统与 `libs`](obr/system.md) | `system.mr`、`std::rout` |
| [作用域](obr/scope.md) · [运算符](obr/operators.md) · [条件分支](obr/control-flow.md) | 解析与运算、`if`/`?:` |

### 引擎（`docs/blink/`）

| | |
|:--|:--|
| [**总索引**](blink/README.md) | 体例、职责表、追溯 |
| [**逻辑速查**](blink/logic-index.md) | 主题 → 文档 → 源码 |
| [架构](blink/architecture.md) | 子包与数据流 |
| [管线](blink/pipeline.md) | `Main` → `ObrInterpreter#run` |
| [工程与预处理](blink/project-preproc.md) | `main.obr`、扫描、`#LINK` 差异 |
| [词法](blink/lexing.md) · [语法](blink/parsing.md) · [AST](blink/ast-reference.md) | `Lexer` · `Parser` · 节点表 |
| [模块](blink/modules.md) | 项目、`libs`、装载 |
| [语义](blink/semantic-binding.md) · [重载](blink/overload-resolution.md) | `SemanticBinder` · `NumericWidening` |
| [运行时](blink/execution.md) · [内部模型](blink/runtime-model.md) | `RuntimeExecutor`、`Value`/`Env` |
| [测试](blink/testing.md) | `src/test` 索引 |
| [错误码](blink/errors.md) · [审计](blink/audit.md) | `E_*`（含 `E_RT_STACK_OVERFLOW` 与 JVM 栈说明）· `InterpreterAuditLog` |
| [实现范围](blink/implementation-scope.md) | 相对全集边界 |
| [支撑类型](blink/supporting.md) · [源码对照](blink/inventory.md) | 工具类 · 每文件→文档 |

---

## 维护约定

- **语言规则** → `docs/obr/`；**引擎实现** → `docs/blink/`。
- 正文不标注「新增/旧版」；同类表格字段全库统一。
