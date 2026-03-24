# 语义绑定（`com.kitepromiss.obr.semantic`）

## `SemanticBinder`

**声明表**：`Map<FunctionSignature, List<DeclaredFunction>>`，由 `collectDeclarations(ModuleBundle)` 从各 `LoadedMrModule` 的 `MrFile` 递归收集 `DeRfunDecl`（含 `namespace` 嵌套）。`FunctionSignature` = 限定名 + 参数类型关键字序列（**不含**返回类型）。

**多模块同签名**：`List` 长度 > 1 → `duplicateDeclError`（消息无前缀 `E_*`）。

---

## 程序级静态登记（`mergeProgramFileStatics`）

- 输入：`ObrProgramBundle` 中每个 `ParsedObrFile` 的 `ObrFile` + 路径。
- 对每个文件调用 `FileStaticRegistry.collect(ast, path)`，合并为 `Map<String, List<FileStaticRegistry.Slot>>`（同名可对应多个槽、不同 `deRfun` 所有者）。
- 结果传入 `bindObrFile`。

---

## `FileStaticRegistry`

- 扫描单 `.obr` 内全部 `deRfun` 体中的 `static var` / `static ident`（`StaticMark`），登记 `Slot`（路径、类型关键字、可见性、所属函数签名）。
- **同函数内**两条 `static var` 同名 → `E_SEM_VAR_DUP`（扫描阶段）。

---

## `bindObrFile` / `bindSingleObr`

对给定 `ObrFile` 中每个 `ObrItem.DeRfunDef`：

1. `sig = FunctionSignature.of(def.name(), def.params())`。
2. `decls.get(sig)` 为空 → `E_SEM_DECL_NOT_FOUND`。
3. 命中唯一 `DeclaredFunction`：审计 `bind_def_resolve`。
4. 声明返回类型与定义返回类型字符串不等 → `E_SEM_RETURN_MISMATCH`。
5. `@Overwrite`：`AccessRule` 解析后，若 `callerRel` 不被允许 → `E_SEM_OVERWRITE_DENIED`。
6. `checkCallsInBody(..., fileStatics, currentSig)`。

**作用域**：`ScopeStack`（块栈 + 形参 + 局部）；`static var` 进入 `VarInfo` 时带 `isStatic`；解析标识符时先词法域，再 `foreignSlots`（其它函数的 file static）。

---

## `checkCallsInBody`

- 形参名 → 类型关键字。
- `void`：`Stmt.Return` → `E_SEM_RETURN_IN_VOID`。
- `Stmt.Return`：`inferType` 与声明返回类型 + `NumericWidening`；否则 `E_SEM_RETURN_VALUE_MISMATCH`。
- `Stmt.Expression` / 赋值 / `++`/`--` / `VarDecl` 等：见各分支；`static` 标记重复等 → `E_SEM_STATIC_MARK_BAD`；文件级 static 同名类型不一致 → `E_SEM_STATIC_FILE_DUP`（解析用名时）。

---

## 调用解析（`resolveCallDeclaration`）

1. 实参 `inferType`。
2. `requested = FunctionSignature(qn, argTypes)`。
3. `decls.get(requested)` 精确命中；否则 `resolveByNumericWiden`（见 [overload-resolution.md](overload-resolution.md)）。
4. `@Callfun` 与调用方路径 → `E_SEM_CALLFUN_DENIED`。
5. 无匹配 → `E_SEM_CALL_SIG_MISMATCH`；并列最小代价 → `E_SEM_OVERLOAD_AMBIGUOUS`。
6. 审计 `bind_call_resolve`。

---

## `inferType` / `inferUnaryType` / `inferBinaryType`

静态推断表达式类型关键字；`E_SEM_TYPE_INFER_*` 见 [errors.md](errors.md)。

---

## `assertUniqueDeRfunDefinitions(ObrProgramBundle)`

跨 `program.files()` 所有 `DeRfunDef`，按 `FunctionSignature` 首次出现路径；重复 → `E_SEM_DUP_DEF`。

---

## 相关类型

- `DeclaredFunction`：模块名 + `MrItem.DeRfunDecl`。
- `FunctionSignature`：`record(qualifiedName, paramTypes)`。
