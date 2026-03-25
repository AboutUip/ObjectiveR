# 测试与可观察行为

Blink 的可执行规格以 **`src/test/java/com/kitepromiss/obr/`** 为准；包路径与主源码 `com.kitepromiss.obr` 镜像。

---

## 运行方式

- 默认 Maven 配置可能跳过测试（见根目录 `pom.xml` 注释）；需要跑测试时使用带测试源码的 profile 或显式打开测试，例如：
  - `mvn test`（在激活了 `has-test-sources` 等配置时）
  - 或按你本地 `pom.xml` 中说明使用 `-P` / `-DskipTests=false`

具体以仓库内 **`pom.xml`** 当前配置为准。

---

## 测试类索引

| 测试类 | 主要覆盖 |
|--------|----------|
| `InterpreterE2ETest` | 解释器端到端 |
| `LexerTest` | 词法 |
| `CharLiteralParserTest` | `CharLiteralParser` |
| `ParserTest` | 语法 |
| `ProjectLocatorTest` | `ProjectLocator` |
| `MrModuleIndexTest` | 模块索引 |
| `ModuleLoaderTest` | `.mr` 装载 |
| `LibsProvisionerTest` | `libs/` 托管 |
| `SemanticBinderTest` / `SemanticBinderDuplicateTest` | 语义绑定 |
| `NumericWideningTest` | 数值加宽 |
| `RuntimeExecutorTest` | 运行时 |
| `ObrExceptionTest` | 错误码解析 |

---

## 栈深度相关用例

- `RuntimeExecutorTest#executeMain_failsOnStackDepthOverflow` 使用**非尾**递归（先 `loop();` 再 `std::rout(1);`），以便在有限 `maxCallDepth` 下稳定得到 `E_RT_STACK_OVERFLOW`。纯 void **尾**自调由运行时优化为循环，同一测试若写成仅末尾 `loop();` 将不会触发该错误（参见 [execution.md](execution.md)）。

---

## 与文档的对应

重大行为变更应：**改源码 → 改/增测试 → 更新 `docs/blink/` 对应专题与 [inventory.md](inventory.md)**。全主题入口见 [logic-index.md](logic-index.md)。
