# 运行时（`com.kitepromiss.obr.runtime.RuntimeExecutor`）

内部类型（`Value`、`Env`、`StaticStore`、栈帧等）见 [runtime-model.md](runtime-model.md)。

## 构造与状态

| 字段 | 含义 |
|------|------|
| `defs` | `FunctionSignature` → `DeRfunDef` |
| `defOrigins` | `FunctionSignature` → 定义所在 `.obr` 路径字符串 |
| `stack` | `Frame(qualifiedName, file)` |
| `functionStaticStores` | 每函数签名 → `StaticStore`（`values` + `types`） |
| `fileStaticSlotsByName` | 合并后的 `FileStaticRegistry` 槽；裸名多激活时读/写规则见 `getValueForName` / `assignWithForeign` |
| `maxCallDepth` | 栈深度上限 |
| `runId` | 审计 |

---

## `executeMain(ObrProgramBundle)`

1. `defs` / `defOrigins` / `functionStaticStores` / `fileStaticSlotsByName` 清空。
2. 对每个 `ParsedObrFile`：`mergeFileStaticRegistry(ast, path)`（由 `FileStaticRegistry.collect` 合并进 `fileStaticSlotsByName`）。
3. 再遍历合并 `DeRfunDef` 入 `defs`；重复签名 → `E_RT_DUP_DEF`。
4. `call("main", List.of(), program.mainPath())`；返回值丢弃。

`executeMain(List<ObrFile>)`：测试用；无 `mainPath` 时用 `"<unknown>"`。

---

## `call(String qn, List<Value> args, String callerFile)`

### void 尾调用（TCO）

若当前函数为 **void**，且**当前语句块的最后一条语句**为 **`Stmt.Expression`** 且 **`expr` 为单独的 `Expr.Invoke`**（`foo();`），且被调函数解析为 **void** 用户函数（非 `std::rout`），则该调用**不**再增加 JVM 调用栈：由 `call` 外层 `while` 承接；入口为 `executeStmtsWithTail`、`tryResolveVoidTailCall`。  
**非尾**位置、或非 `Invoke` 根表达式（如 `a + b;`）、或表达式中的调用（如 `std::rout(f())`），均不应用此优化。

语言规范侧对调用栈的意图说明见 [`docs/obr/runtime.md`](../obr/runtime.md) §5.2；`E_RT_STACK_OVERFLOW` 与 JVM `StackOverflowError` 的对照见 [errors.md](errors.md)。

### `std::rout`

- 须恰好一个实参。
- `Value` 类型为 `string`、`char`、`int`、`long`、`float`、`double` 时按行输出；否则 `E_RT_ROUT_ARG`。
- `system.mr` 中声明 `byte`/`short` 重载；运行时 `Value` 无独立 byte/short 标量，重载解析与数值类型一致后通常以 `int` 等承载。
- 返回 `null`；审计 `call_resolve`（`defined_in=libs/system.obr(native)`）。

### 用户函数

- `actualTypes` = 各 `Value.type().keyword`。
- `resolveRuntimeSignature`：精确键优先，再 `NumericWidening`（见 [overload-resolution.md](overload-resolution.md)）；二义性 `E_RT_OVERLOAD_AMBIGUOUS`；无实现 `E_RT_IMPL_NOT_FOUND`。
- 栈深度 ≥ `maxCallDepth` → `E_RT_STACK_OVERFLOW`。
- 审计 `call_resolve`、`call_enter`；`stack.push`。
- 形参绑定到 `Env`（`Env.push` + `putLocal`）。
- `StaticStore statics = functionStaticStores.computeIfAbsent(resolved, …)`。
- 顺序执行 `body` 语句：`executeStmtsWithTail` 遍历列表；`Block` 在循环内 `env.push` / `executeStmtsWithTail` 子列表 / `env.pop`；其余变体交给 `executeStmtWithoutBlock`（见下）。

---

## 语句（`executeStmtsWithTail` / `executeStmtWithoutBlock`）

| `Stmt` | 行为 |
|--------|------|
| `Block` | 仅由 `executeStmtsWithTail` 处理（不在 `executeStmtWithoutBlock` 的 `switch` 中） |
| **`If`** | 对条件 `evalExpr`；为真执行 `then` 子句，否则在有 `else` 时执行 `else`（单条或块由 `executeBranch` 处理）；与 void **尾调用**组合时通过 `StmtExecResult.TailVoidCall` 向上传递 |
| **`While`** | 每轮先对条件 `evalExpr`，假则结束；真则执行循环体。体为 **`{ … }`** 时用 `executeBranch`（与块一致，每轮 `env.push`/`pop`）；**非块**单语句体每轮额外 `env.push`/`pop`。`ReturnedVal` / `TailVoidCall` 与 `If` 一样向上传递；循环体内 **`BreakLoop`** / **`ContinueLoop`** 由本语句消费（`break` 结束循环，`continue` 跳过本轮剩余语句并进入下一轮条件判断） |
| **`Break`** / **`Continue`** | 分别返回 **`BreakLoop`** / **`ContinueLoop`**，向上传递直至被最近的 `While` 处理；若逃逸函数体则 `E_RT_*`（语义应已拒绝循环外使用） |
| **`Nop`** | 无操作 |
| `Expression` | `evalExprStmtDiscard`：根为 `Invoke` 时 `executeCall`（void 可）；否则 `evalExpr` 丢弃结果 |
| `Return` | `evalExpr` → `widenReturnValue` → 返回 |
| `VarDecl` | `executeVarDecl`；`static` 且同名槽已存在 → `E_RT_STATIC_LOCAL_REDECL`（见 [`docs/obr/runtime.md`](../obr/runtime.md) §5.1） |
| `Assign` | 含 `string` 的 `+=` 与数值复合赋值等；复合未初始化等 → `E_RT_COMPOUND_UNINIT`（若适用） |
| `Update` | `++`/`--` |
| `StaticMark` | 局部迁入 `statics` |

非 void 正常结束无 `return` → `E_RT_RETURN_MISSING`。

---

## `evalExpr`

| `Expr` | 行为 |
|--------|------|
| `Literal` | 字面量 → `Value` |
| `NameRef` | `getValueForName`；文件级 static 多激活 → `E_RT_STATIC_FILE_DUP` |
| `Invoke` | `call`；void 作值 → `E_RT_EXPR_UNSUPPORTED`（表达式语句顶层除外，见上） |
| **`Assign`** | `evalAssignExpression`（与 `Stmt.Assign` 同路径）；求值为左变量类型对应的已写入值 |
| **`Conditional`** | 先求条件；再求所选分支（**不**求值未选分支） |
| `Unary` / `Binary` | **`&&`/`||`**：**短路**；**比较/相等**：整型关系、`string` 引用相等等；**`|^&` / 移位**：`ToInt32`/`ToUint32`、移位量 mod 32（见 `evalBitwiseOrShift`）；**算术/幂**：见 `evalBinary` / `evalPowExpr`；**`+`**：数值或 **字符串拼接**（`valueToConcatString` 等） |
| `PrefixUpdate` / `PostfixUpdate` | `++`/`--`；后缀值为旧值 |

**整型除零**：`int`/`long` 的 `/` 与 `%` 在除数为 0 时 → **`E_RT_INTEGER_DIV_ZERO`**（见 [errors.md](errors.md)）。`float`/`double` 不按该码抛出。

未覆盖的变体 → `E_RT_EXPR_UNSUPPORTED`。

---

## 返回与加宽

`widenReturnValue(declaredKeyword, raw)`：`NumericWidening.oneArgCost`；失败 `E_RT_RETURN_COERCE`。

---

## `Value` / `ValueType`

字段与工厂方法见 [runtime-model.md](runtime-model.md)；与 `FunctionSignature` 类型关键字字符串对齐。

---

## 错误码

见 [errors.md](errors.md) 中全部 `E_RT_*`。
