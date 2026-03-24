# 解释器构建（`products` 目录）

**脚本与分发产物**统一放在本目录：

| 路径 | 说明 |
|------|------|
| `build-jar.ps1` / `build-win-exe.ps1` | 一键调用 Maven |
| `out/` | **构建产物**（由 Maven 生成，已加入 `.gitignore`） |

## 产物位置（`out/`）

| 构建方式 | 产物 |
|----------|------|
| `.\build-jar.ps1` 或 `mvn package` | `out/BlinkEngine.jar`（同时仍在 `target/`，便于调试） |
| `.\build-win-exe.ps1`（Windows） | `out/BlinkEngine.exe` 及运行时文件**直接位于 `out/`**（脚本在 jpackage 后去掉多余的 `BlinkEngine/` 子目录） |
| `mvn package -Pwin-exe`（不用脚本） | jpackage 默认仍为 `out/BlinkEngine/BlinkEngine.exe`（多一层目录）；若需与脚本一致，请用脚本构建或手动展平 |

在 PowerShell 中进入 **`products`** 后执行脚本即可。

## 用法

```powershell
cd products
.\build-jar.ps1
.\build-jar.ps1 -SkipTests

.\build-win-exe.ps1
.\build-win-exe.ps1 -SkipTests
```

## 维护说明

1. **`pom.xml`** 中 `products.out.dir` 指向 `products/out`；fat jar 在 package 阶段复制到该目录；`win-exe` profile 将 jpackage 的 `--dest` 设为同一目录。
2. **`mvn clean`** 会清理 `products/out/`（见 `maven-clean-plugin` 配置）。
3. 若修改应用名（`--name` / jpackage 子目录名），请同步更新 **`pom.xml`** 与 **`build-win-exe.ps1`** 中函数 **`Expand-JpackageAppFolder`** 的 `-NestedName`（默认 `BlinkEngine`）及 **`BlinkEngine.exe`** 文件名（若应用名变更）。

脚本内控制台输出为英文（兼容 Windows PowerShell 5.1 + UTF-8 BOM）。

## Windows 可执行文件（常见问题）

- **入口名称**：当前 jpackage 应用名为 **BlinkEngine**，请运行 **`products\out\BlinkEngine.exe`**，不要运行旧的 **`ObjectiveR.exe`**。旧启动器会去找 **`app\ObjectiveR.cfg`**，而新产物为 **`app\BlinkEngine.cfg`**，会报 `No such file or directory`。
- **重新构建后**：`build-win-exe.ps1` 会在成功展平后**删除** `products\out\ObjectiveR.exe` 及多余的 `ObjectiveR\` 目录（若仍存在）。若你手动拷贝过旧 exe，请自行删掉以免误点。
- **运行示例**（在 `products\out` 下）：`.\BlinkEngine.exe ..\..\demo`（路径按你的仓库位置调整）。
