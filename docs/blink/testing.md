# 测试与可观察行为

Blink 的回归与单元测试位于 **`src/test/java/`**；与主包 `com.kitepromiss.obr` 对应，**测试夹具**放在 **`com.kitepromiss.obr.testsupport`**（非 JUnit 测试类，无 `@Test`）。

---

## 运行方式

- 根目录 **`pom.xml`**：若存在目录 **`src/test`**，profile **`has-test-sources`** 会激活，默认 **`mvn package` / `mvn test` 会编译并执行测试**。
- 若需跳过：`mvn -DskipTests` 或按 `pom.xml` 注释使用 **`-Dmaven.test.skip=true`**。

具体以仓库内 **`pom.xml`** 当前配置为准。

---

## 测试类与职责

| 测试类 | 主要覆盖 |
|--------|----------|
| **`BlinkRuntimeE2ETest`** | 端到端：`if`/`else if`、空 `;`、`?:`、比较与相等、`&&`/`||` 短路、字符串拼接与 `+=`、整型除零/`E_RT_INTEGER_DIV_ZERO`、浮点除零不抛整型除零码 |
| **`BlinkSemanticNegativeTest`** | 语义拒绝：如相等两侧类型不一致、`?:` 两分支类型不一致 |
| **`BlinkLexerParserTest`** | 词法：单 `&` / 单 `|` 报错；语法：`if` 与 `?:` 的 AST |
| **`NumericExprTypingTest`** | `NumericExprTyping`（byte 混用、提升、幂结果类型） |

### 测试夹具

| 类型 | 说明 |
|------|------|
| **`com.kitepromiss.obr.testsupport.BlinkObrTestSupport`** | 在临时项目根写入 `main.obr` 与规范 `libs/`，执行与 `ObrInterpreter` 一致的加载、版本检查、语义绑定、运行；供 E2E 与语义测试复用 |

---

## 与文档的对应

重大行为变更应：**改源码 → 改/增测试 → 更新 `docs/blink/` 对应专题与 [inventory.md](inventory.md)**。全主题入口见 [logic-index.md](logic-index.md)。相对语言规范的全集边界见 [implementation-scope.md](implementation-scope.md)。
