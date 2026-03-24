# 运行时（`com.kitepromiss.obr.runtime.RuntimeExecutor`）

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
- 顺序执行 `body` 语句（见下）。

---

## 语句 `executeStmt`

| `Stmt` | 行为 |
|--------|------|
| `Expression` | `executeCall` |
| `Return` | `evalExpr` → `widenReturnValue` → 返回 |
| `Block` | `env.push` + 子语句 + `env.pop` |
| `VarDecl` | `executeVarDecl`；`static` 且同名槽已存在 → `E_RT_STATIC_LOCAL_REDECL`（见 [`docs/obr/runtime.md`](../obr/runtime.md) §5.1） |
| `Assign` | 复合未初始化等 → `E_RT_COMPOUND_UNINIT`（若适用） |
| `Update` | `++`/`--` |
| `StaticMark` | 局部迁入 `statics` |

非 void 正常结束无 `return` → `E_RT_RETURN_MISSING`。

---

## `evalExpr`

| `Expr` | 行为 |
|--------|------|
| `Literal` | 字面量 → `Value` |
| `NameRef` | `getValueForName`；文件级 static 多激活 → `E_RT_STATIC_FILE_DUP` |
| `Invoke` | `call`；void 作值 → `E_RT_EXPR_UNSUPPORTED` |
| `Unary` / `Binary` | 算术/逻辑非/按位非/幂等 |
| `PrefixUpdate` / `PostfixUpdate` | `++`/`--`；后缀值为旧值 |

未覆盖的变体 → `E_RT_EXPR_UNSUPPORTED`。

---

## 返回与加宽

`widenReturnValue(declaredKeyword, raw)`：`NumericWidening.oneArgCost`；失败 `E_RT_RETURN_COERCE`。

---

## `Value` / `ValueType`

`RuntimeExecutor` 内私有 `record Value` / `enum ValueType`；与 `FunctionSignature` 类型关键字字符串对齐。

---

## 错误码

见 [errors.md](errors.md) 中全部 `E_RT_*`。
