# 实现范围（相对 `docs/obr/` 全集）

本节列出 **当前代码路径下** 可依赖的行为；未列出的规范条目视为**未实现、不完整或可能偏差**，以 `src/test` 与源码为准。分阶段细节见 [README.md](README.md) 索引中的 [parsing.md](parsing.md)、[semantic-binding.md](semantic-binding.md)、[execution.md](execution.md) 等。

---

## 语言版本

- `ObrLanguageVersion.SUPPORTED` 与 `VersionDirectiveChecker` 一致；非支持版本号 → `E_VER_UNSUPPORTED`（见 [errors.md](errors.md)）。

---

## 顶层与模块

- `.obr`：`deRfun`、预处理行、`import`（由 `Parser` / `ObrProgramLoader` 覆盖的范围）。
- `.mr`：声明、`namespace`（`Parser#parseMrFile`）。
- `import` 解析与 `MrModuleIndex`：模块名唯一路径；冲突抛错（消息无前缀码，见 [errors.md](errors.md)）。

---

## 语句与表达式（静态 + 运行）

- **语句**：`Expression`（调用）；`Return`；`Block`；`VarDecl`（`var` / `static var` / `public|private static var`）；`Assign`（`=` 与 `+=` 等复合赋值）；`Update`（语句级前缀/后缀 `++`/`--`）；`StaticMark`（`static ident;`）。
- **表达式**：字面量、`NameRef`、`Unary`、`Binary`（含 `**`）、`Invoke`、`PrefixUpdate` / `PostfixUpdate`（表达式级 `++`/`--`）。
- **未实现（相对 `docs/obr/operators.md` 全集）**：关系/相等/位运算（除 `~` 一元）、逻辑 `&&`/`||` 等未在 `Parser`/`evalExpr` 覆盖。
- **一元/二元静态规则**：`SemanticBinder#inferType` 等；不一致抛 `E_SEM_TYPE_INFER_EXPR` 等。

---

## 调用与重载

- 声明键：`FunctionSignature`（限定名 + 参数类型关键字列表）。
- 数值重载：`NumericWidening` + `SemanticBinder.resolveByNumericWiden` + `RuntimeExecutor.resolveRuntimeSignature`（见 [overload-resolution.md](overload-resolution.md)）。

---

## 运行时

- **调用栈**：`RuntimeExecutor` 内 `ArrayDeque<Frame>`；深度上限构造参数，溢出 `E_RT_STACK_OVERFLOW`。
- **`return`**：非 void 须执行到 `Stmt.Return`；否则 `E_RT_RETURN_MISSING`；返回类型数值加宽失败 `E_RT_RETURN_COERCE`。
- **`static var`**：再次执行声明且同名槽已存在 → `E_RT_STATIC_LOCAL_REDECL`（见 [`docs/obr/runtime.md`](../obr/runtime.md) §5.1）。

---

## 未作为完整目标实现的示例（非穷举）

- 关系/相等/逻辑与或等未在 `Parser`/`evalExpr` 覆盖的运算符。

具体以 `Parser`、`SemanticBinder`、`RuntimeExecutor` 分支为准。
