<div align="center"><img src="../assets/obr-logo.svg" alt="Obr" width="72" height="72"/></div>

# ObjectiveR 语言规范索引

本目录按主题拆分维护 ObjectiveR（Obr）语言规范。阅读顺序建议：先 [规范总览](overview.md)，再按需求进入下列专题。

## 规范索引

| 文档 | 说明 |
|------|------|
| [overview.md](overview.md) | 规范总览（范围、**规范文档 Draft/Stable**、版本策略、基础约定、规范分层） |
| [preprocessor.md](preprocessor.md) | 预处理指令（`#VERSION`、`#LINK`） |
| [runtime.md](runtime.md) | 执行模型（启动输入、唯一 `main.obr`、根目录、`libs/`、资源扫描、`static var`、入口函数、调用栈与实现注记 §5.2） |
| [system.md](system.md) | `system.mr`、`namespace std`/`std::rout`、`libs/system.obr`、隐式 `import system`、`libs/` 托管 |
| [visibility.md](visibility.md) | 可见性（`public`、`private`、默认可见性） |
| [static.md](static.md) | `static`、静态表、唯一性与生命周期 |
| [termination.md](termination.md) | 语句终止（`;`）、预处理行、逗号续行 |
| [moduleR.md](moduleR.md) | `.mr`、`import`、`namespace`/`::`、`deRfun`、`@Overwrite`、冻结与恢复 |
| [comments.md](comments.md) | 注释（`//` 与 `/* ... */`） |
| [types.md](types.md) | 基础类型、`undefined`/`null`、字面量与位宽 |
| [scope.md](scope.md) | 作用域、标识符词法、解析顺序 |
| [operators.md](operators.md) | 运算符、隐性转化、§1.2 数值提升（JS `Number`）、`ToBoolean`、优先级 |
| [control-flow.md](control-flow.md) | `if` / `else` / `else if`、`?:`、条件与布尔转换 |

**上级目录**：[docs 说明](../README.md)

**实现文档**：[Blink 索引](../blink/README.md) · **编辑器扩展**：[Expansion](../../Expansion/README.md)

## 维护原则

- 规则增删改对应专题文件；与实现行为对照时引用 `docs/blink/` 与测试，不在此目录写实现细节堆砌。
- 措辞：`必须` / `应当` / `可选`。
