# 模块与多文件（`com.kitepromiss.obr.module`）

工程根判定、`.obr` 扫描与预处理在 Blink 中的**完整语义**（含 **`#LINK` 当前不参与改根**）见 [project-preproc.md](project-preproc.md)。

---

## `ProjectLocator`（`project` 包）

| 方法 | 行为 |
|------|------|
| `ProjectLocator.resolve(Path)` | 参数为**已存在**路径：文件则须文件名为 `main.obr`（大小写敏感），项目根为父目录；目录则递归查找唯一 `main.obr`，多文件报错 |

失败抛 `ObrException`（无前缀码），消息描述路径/唯一性。与语言规范中 `#LINK` 的差异见 [project-preproc.md](project-preproc.md)。

---

## `LibsProvisioner`

- 路径：`projectRoot/libs/`。
- **`ensure(projectRoot)`**：若已存在 `libs/`，则**整目录删除**后重建，再写入内嵌规范文本 `system.mr`、`system.obr`（与 [`docs/obr/system.md`](../obr/system.md) 清单一致）。审计事件 `libs_replace_existing`（见 [audit.md](audit.md)）。
- **`cleanup(projectRoot)`**：删除 `projectRoot/libs/`（不存在则忽略）；由 `ObrInterpreter#run` 的 `finally` 调用。
- 常量：`SYSTEM_MODULE_NAME = "system"`（隐式 `import` 与模块名）。

---

## `ObrProgramBundle`

| 字段 | 含义 |
|------|------|
| `mainPath` | 入口 `main.obr` 路径 |
| `mainAst` | 入口文件已解析的 `ObrFile`（与 loader 中第一项一致） |
| `files` | `List<ParsedObrFile>`，`ParsedObrFile(path, ast)` |

---

## `ObrProgramLoader#loadAllObr`

1. 将 `mainPath` + `mainAst` 作为第一项。
2. `Files.walkFileTree(projectRoot)`：对每个 `*.obr`（非入口路径）读源 → `Lexer.readAllTokens` → `Parser#parseObrFile`，追加 `ParsedObrFile`。
3. 跳过目录：`.git`、`target`、`build`、`.idea`、`node_modules`。

---

## `MrModuleIndex#scan`

- 遍历 `projectRoot` 下所有 `*.mr`（扩展名比较用 `toLowerCase(Locale.ROOT)`）。
- 模块名 = 文件名去掉 `.mr`；**同一模块名对应多路径** → `ObrException`（无前缀码）。
- 产出：`MrModuleIndex(moduleToPath)`，`require(name)` 取路径。

---

## `ModuleLoader#load`

1. `MrModuleIndex.scan(projectRoot)`。
2. `importLoadOrder(mainObr)`：**顺序** = `[system]` + `main.obr` 顶层 `import` 顺序**去重**（`LinkedHashSet`）。
3. 对每个模块名：`index.require` → 读源 → `Lexer` + `Parser#parseMrFile` → `LoadedMrModule`。
4. `ModuleBundle.of(loaded)`。

审计：`module_index_built`、`mr_load_begin`、`mr_load_end`（见 [audit.md](audit.md)）。

---

## 与语义/运行的关系

- `SemanticBinder` 使用 `ModuleBundle` 收集 `.mr` 声明（`collectDeclarations`）。
- `FileStaticRegistry.collect` / `mergeProgramFileStatics`：见 [semantic-binding.md](semantic-binding.md)。
- `RuntimeExecutor` 使用 `ObrProgramBundle` 合并全部 `.obr` 中的 `deRfun` 定义表，**不**再次解析 `.mr`；运行前合并 file static 登记，见 [execution.md](execution.md)。
