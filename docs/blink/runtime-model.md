# 运行时内部模型（`RuntimeExecutor`）

`RuntimeExecutor` 内 **非 public** 类型承载栈、环境与值。对外行为见 [execution.md](execution.md)；本页便于查「Java 里长什么样」。

---

## `ValueType`（枚举）

与 Obr 类型关键字对应（`keyword()` 返回值）：

| 枚举常量 | 关键字 |
|----------|--------|
| `STRING` | `string` |
| `CHAR` | `char` |
| `INT` | `int` |
| `LONG` | `long` |
| `FLOAT` | `float` |
| `DOUBLE` | `double` |
| `BOOLEAN` | `boolean` |
| `NULL_VALUE` | `null` |
| `UNDEFINED` | `undefined` |

**说明**：`byte` / `short` 在模块声明中存在；运行时不单独存标量，经重载解析后常以 `int` 等承载（见 [execution.md](execution.md) `std::rout` 节）。

---

## `Value`（record）

- 字段：`ValueType type`, `Object value`（按 `type` 解释为 `String`、`Character`、`Integer`、`Long`、`Float`、`Double`、`Boolean` 或 `null`）。
- 工厂方法：`ofString`、`ofChar`、`ofInt`、`ofLong`、`ofFloat`、`ofDouble`、`ofBoolean`、`ofNull`、`ofUndefined`。
- 取值辅助：`asString`、`booleanValue`、`charValue`、`intValue`、`longValue`、`floatValue`、`doubleValue`。

字面量、运算、调用返回值均归一为上述 `Value`。

---

## `StaticStore`（函数级 static）

- `Map<String, Value> values`：函数内 `static var` / `static mark` 后的槽位值。
- `Map<String, String> types`：同名槽的类型关键字（用于复合赋值等）。

**语义**：槽跨调用保留；**每次**执行到 `static var` 且同名槽已存在 → `E_RT_STATIC_LOCAL_REDECL`（见 [execution.md](execution.md)）。

---

## `fileStaticSlotsByName`

- 类型：`Map<String, List<FileStaticRegistry.Slot>>`。
- 来源：对每个 `ParsedObrFile` 调用 `FileStaticRegistry.collect`，再按名合并列表。
- 裸名解析：多槽已激活时冲突 → `E_RT_STATIC_FILE_DUP`（见 `RuntimeExecutor` 内 `getValueForName` / `assignWithForeign` 等）。

`Slot` 字段见 [semantic-binding.md](semantic-binding.md) / 源码 `FileStaticRegistry.Slot`。

---

## `Env`（块与词法链）

- `List<Map<String, Value>> scopes` 与 `List<Map<String, String>> typeScopes` 同步 push/pop。
- `putLocal`：写入最内层作用域。
- `getValue` / `getDeclaredType`：自内向外扫作用域，再查当前函数的 `StaticStore`。
- `tryAssign` / `assign`：自内向外找槽；局部优先，否则函数 static。
- `removeFromInnermost`：复合赋值等场景下临时取出内层绑定。

---

## 调用栈

- `ArrayDeque<Frame> stack`，`Frame` 为 `record(String qn, String file)`（限定名与定义所在文件路径字符串）。
- 深度与 `maxCallDepth`（构造参数，CLI 路径上为 1024）比较，溢出 → `E_RT_STACK_OVERFLOW`（与 JVM 线程栈无关，见 [errors.md](errors.md)）。
- **void 尾调用**：满足条件时 `call` 在单线程循环内接续下一被调函数，逻辑上仍产生 `call_enter` / `call_exit` 审计事件，但**不**为该类跳转增加 JVM 栈帧；详见 [execution.md](execution.md)。

---

## 其它内部 record

| 类型 | 用途 |
|------|------|
| `RtResult` | `boolean returned`, `Value value`；语句块是否因 `return` 结束 |
| `LocalBinding` | `String typeKeyword`, `Value value`；从环境中卸下一处绑定 |

---

## 定义表

- `Map<FunctionSignature, ObrItem.DeRfunDef> defs`
- `Map<FunctionSignature, String> defOrigins`：定义来源路径（诊断用）

合并规则见 [execution.md](execution.md) `executeMain`。
