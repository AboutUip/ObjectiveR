# 执行管线（`ObrInterpreter#run`）

**入口**：`com.kitepromiss.Main#main` → `LaunchArgs.parse` → `ObrInterpreter#run(LaunchArgs)`。

**返回**：`0` 成功；`1` 为 `ObrException` 或读源 `IOException`（审计中 IO 记为 `E_IO_READ`）。

---

## `LaunchArgs`

| 行为 | 说明 |
|------|------|
| 路径 | 首个非选项参数为 `input`（`Path`）；无参数则为 `.` |
| trace | `--trace=<level>`、`--trace <level>`、`-v`（VERBOSE）、`-q`（OFF）；未指定则为 `OFF` |
| 非法选项 | 以 `-` 开头且未识别 → `ObrException`（无前缀 `E_*`） |

---

## 顺序（与源码一致）

| 序号 | 操作 | 类 / 方法 | 说明文档 |
|------|------|-----------|----------|
| 0 | 进程入口 | `com.kitepromiss.Main#main` → `LaunchArgs.parse` | — |
| 1 | 解析启动输入 | `project.ProjectLocator#resolve` | [modules.md](modules.md) |
| 2 | 维护 `libs/` | `LibsProvisioner#ensure` | [modules.md](modules.md) |
| 3 | 读入口源 | `Files.readString(main)` | — |
| 4 | 词法 | `Lexer#readAllTokens` | [lexing.md](lexing.md) |
| 5 | 语法（入口） | `Parser#parseObrFile` | [parsing.md](parsing.md) |
| 6 | 装载全部 `.obr` | `ObrProgramLoader#loadAllObr` | [modules.md](modules.md) |
| 7 | `#VERSION` | `VersionDirectiveChecker#checkProgram` | [version-directive.md](version-directive.md) |
| 8 | `deRfun` 签名唯一 | `SemanticBinder#assertUniqueDeRfunDefinitions` | [semantic-binding.md](semantic-binding.md) |
| 9 | 按文件语义绑定 | `ModuleLoader#load` → `SemanticBinder#bindObrFile` | [modules.md](modules.md)、[semantic-binding.md](semantic-binding.md) |
| 10 | 运行 | `RuntimeExecutor#executeMain` | [execution.md](execution.md) |
| 11 | 清理 `libs/` | `LibsProvisioner#cleanup`（`ObrInterpreter#run` 的 `finally`） | [modules.md](modules.md) |

已解析出 `projectRoot` 时，`finally` 调用 `LibsProvisioner.cleanup`（与 [`docs/obr/runtime.md`](../obr/runtime.md) §3.1、`system.md` 第 2 节一致）。

---

## `ObrProgramLoader` 扫描规则

见 [modules.md](modules.md)。

---

## `ModuleLoader#load`（每个 `.obr` 一次）

见 [modules.md](modules.md)。

---

## `RuntimeExecutor#executeMain`

见 [execution.md](execution.md)。

审计相位见 [audit.md](audit.md)。
