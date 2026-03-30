# ObjectiveR File Support

**Obr** (`.obr`) / **ModuleR** (`.mr`) for VS Code / Cursor.

## Features

| 能力 | 说明 |
|------|------|
| **语法着色** | TextMate + **语义高亮**（需主题开启 `editor.semanticHighlighting`）：`deRfun` 名、形参、`var` 声明与引用、已知函数调用、`ns::` 段；与 TextMate 互补 |
| **项目索引** | `objectiveR.project.scanMode`：`workspace`（全工作区）或 `mainObrAndLink`（解析 `main.obr` 的 `#LINK` 后在根下并集扫描 `.obr`/`.mr`）；任意 `.obr`/`.mr` 变更会防抖重建索引与语义 |
| **自动补全** | 关键字（含 `if`/`else`）；工作区已索引的 `deRfun`（含 `::`）；`import` 模块名；`std::` 下 `rout`；`var` / `static var` 片段 |
| **语法检查** | 括号/引号/块注释平衡；`.mr` 须 `;` 声明、`.obr` 须 `{` 实现；重复签名；`import` 与注解形式；`main.obr` 提示 |
| **其它** | Hover、签名帮助、定义/引用跳转；文件图标主题；片段 |

设置前缀：`objectiveR.`。可单独关闭补全、结构/工作区诊断、**语义高亮**（`objectiveR.semanticHighlighting.enable`）或切换 **扫描模式**（`objectiveR.project.scanMode`）。

## Pack VSIX

```bash
npx --yes @vscode/vsce package
```

安装 VSIX 后可选将 **File Icon Theme** 设为 `ObjectiveR Icons`。

## 与语言实现

行为以仓库 **BlinkEngine** 与 `docs/blink/` 为准；本扩展不嵌入解释器。
