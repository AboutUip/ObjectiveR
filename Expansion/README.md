# Expansion（编辑器扩展与二次开发）

本目录存放 **与 BlinkEngine 解释器配套、但独立发布** 的扩展资源，便于其他用户：

- **自行打包安装**：在 VS Code / Cursor 等编辑器中识别 `.obr` / `.mr`、语法高亮、片段与文件图标；
- **Fork 后二次开发**：在现有扩展上增加语法规则、片段、主题或发布到你自己的 Publisher。

> **说明**：这里的「扩展」指 **编辑器扩展（VS Code 兼容）**，**不是** Java 解释器内的插件加载机制。解释器行为仍以仓库主工程与 [`docs/blink/`](../docs/blink/README.md) 为准。

---

## 目录里有什么

当前内置示例如下（目录名含历史版本号，**发布版本以子目录内 `package.json` 的 `version` 为准**）：

| 路径 | 内容 |
|------|------|
| [`kitepromiss.objective-r-file-support-0.0.1/`](kitepromiss.objective-r-file-support-0.0.1/) | **ObjectiveR File Support**（当前 `package.json` **0.1.1**）：语法高亮、补全、诊断、Hover/跳转、片段、图标主题；与 Blink 同步 `if`/`else`、比较与逻辑运算符、`#VERSION`/`#LINK` 片段格式等。 |

扩展清单以各子目录内 `package.json` 的 `name`、`version`、`publisher` 为准；子目录 [`README.md`](kitepromiss.objective-r-file-support-0.0.1/README.md) 提供英文简要说明与打包命令。

---

## 环境要求（打包与本地开发）

- **Node.js**（LTS 推荐）
- **npm**
- 全局安装打包工具（任选其一）：
  - `npm i -g @vscode/vsce`（官方常用）
  - 或使用 `npx @vscode/vsce package` 免全局安装

---

## 从源码打包为 VSIX

1. 进入具体扩展目录，例如：
   ```bash
   cd Expansion/kitepromiss.objective-r-file-support-0.0.1
   ```
2. 若 `package.json` 声明了依赖，先执行：
   ```bash
   npm install
   ```
   （当前示例扩展无额外 npm 依赖时可直接打包。）
3. 生成安装包：
   ```bash
   vsce package
   ```
   或在仓库根目录：
   ```bash
   npx --yes @vscode/vsce package --cwd Expansion/kitepromiss.objective-r-file-support-0.0.1
   ```
4. 得到 `*.vsix` 文件后，在 **VS Code / Cursor** 中：`Extensions: Install from VSIX...`，选择该文件。

安装后可在命令面板中将 **File Icon Theme** 设为扩展提供的图标主题（见子目录 README）。

---

## 二次开发建议

1. **复制目录**：复制整个扩展文件夹，修改 `package.json` 中的 `name`、`displayName`、`publisher`、`version`，避免与官方扩展 ID 冲突。
2. **语法高亮**：编辑 `syntaxes/*.tmLanguage.json`（TextMate 语法）；与 [`docs/obr/`](../docs/obr/README.md) 中的关键字、运算符保持同步更易维护（含 `if`/`else`、`==`/`&&`/`||` 等与 Blink 词法一致）。
3. **片段**：编辑 `snippets/*.code-snippets`，减轻手写 `deRfun`、`import` 等样板。
4. **图标与主题**：替换 `icons/` 下 SVG，或调整 `objective-r-icon-theme.json`。
5. **发布到市场**：需 [Azure DevOps Personal Access Token](https://code.visualstudio.com/api/working-with-extensions/publishing-extension) 等，使用 `vsce publish`（详见 VS Code 官方文档）。

欢迎通过 Issue / PR 向本仓库贡献通用改进；若仅自用，保持本地 fork 即可。

---

## 与主仓库的关系

| 仓库区域 | 作用 |
|----------|------|
| `src/main/java/...`（BlinkEngine） | Obr 语言解释执行 |
| `docs/obr/` | 语言规范 |
| `docs/blink/` | 解释器实现说明 |
| **`Expansion/`**（本目录） | 编辑器侧体验：高亮、图标、片段等 |

提交本目录变更时，请在 PR 中说明对用户安装步骤的影响（例如 `version` 升级、是否需要重新打包 VSIX）。
