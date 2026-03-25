# 错误码与抛出位置

`ObrException#getMessage` 通常以 **`E_XXX` 前缀 + 空格 + 说明** 形式出现；`ObrException#extractErrorCode` 解析该前缀。

`#VERSION` 语义与正则见 [version-directive.md](version-directive.md)；下列 `E_PREPROC_*` / `E_VER_*` 与之一致。

下列 **前缀码** 与 **定义类** 对应；**触发条件** 以消息语义为准。

---

## 语义（`SemanticBinder` / `FileStaticRegistry`）

| 前缀码 | 定义位置 |
|--------|----------|
| `E_SEM_DUP_DEF` | `SemanticBinder` |
| `E_SEM_DECL_NOT_FOUND` | `SemanticBinder` |
| `E_SEM_RETURN_MISMATCH` | `SemanticBinder` |
| `E_SEM_OVERWRITE_DENIED` | `SemanticBinder` |
| `E_SEM_CALL_SIG_MISMATCH` | `SemanticBinder` |
| `E_SEM_CALLFUN_DENIED` | `SemanticBinder` |
| `E_SEM_OVERLOAD_AMBIGUOUS` | `SemanticBinder` |
| `E_SEM_TYPE_INFER_LITERAL` | `SemanticBinder` |
| `E_SEM_TYPE_INFER_NAME` | `SemanticBinder` |
| `E_SEM_TYPE_INFER_EXPR` | `SemanticBinder` |
| `E_SEM_VOID_EXPR` | `SemanticBinder` |
| `E_SEM_RETURN_IN_VOID` | `SemanticBinder` |
| `E_SEM_RETURN_VALUE_MISMATCH` | `SemanticBinder` |
| `E_SEM_VAR_DUP` | `SemanticBinder`、`FileStaticRegistry` |
| `E_SEM_ASSIGN_TYPE` | `SemanticBinder` |
| `E_SEM_STATIC_MARK_BAD` | `SemanticBinder`、`FileStaticRegistry` |
| `E_SEM_STATIC_FILE_DUP` | `SemanticBinder` |

---

## 预处理版本（`VersionDirectiveChecker`）

| 前缀码 | 类 |
|--------|-----|
| `E_PREPROC_VERSION_PARSE` | `VersionDirectiveChecker` |
| `E_VER_UNSUPPORTED` | `VersionDirectiveChecker` |
| `E_VER_MISMATCH` | `VersionDirectiveChecker` |

---

## 运行时（`RuntimeExecutor`）

| 前缀码 | 类 |
|--------|-----|
| `E_RT_ROUT_ARG` | `RuntimeExecutor` |
| `E_RT_IMPL_NOT_FOUND` | `RuntimeExecutor` |
| `E_RT_STACK_OVERFLOW` | `RuntimeExecutor`（逻辑栈 `Frame` 深度 ≥ `maxCallDepth`，见 [execution.md](execution.md)） |
| `E_RT_OVERLOAD_AMBIGUOUS` | `RuntimeExecutor` |
| `E_RT_LITERAL_UNSUPPORTED` | `RuntimeExecutor` |
| `E_RT_NAME_UNKNOWN` | `RuntimeExecutor` |
| `E_RT_EXPR_UNSUPPORTED` | `RuntimeExecutor` |
| `E_RT_DUP_DEF` | `RuntimeExecutor` |
| `E_RT_RETURN_MISSING` | `RuntimeExecutor` |
| `E_RT_RETURN_COERCE` | `RuntimeExecutor` |
| `E_RT_STATIC_FILE_DUP` | `RuntimeExecutor`（文件级 static 同名冲突） |
| `E_RT_STATIC_LOCAL_REDECL` | `RuntimeExecutor`（同一函数再次执行 `static var` 且同名槽已存在） |
| `E_RT_COMPOUND_UNINIT` | `RuntimeExecutor` |
| `E_RT_INTEGER_DIV_ZERO` | `RuntimeExecutor`（`byte`/`short`/`int`/`long` 的 `/` 或 `%` 除数为 `0`，见 [`docs/obr/operators.md`](../obr/operators.md) §6.5） |

**与 JVM `StackOverflowError` 的区别**：后者为 Java 线程栈耗尽，常出现在**非尾**的深递归路径上，且深度往往**小于** `maxCallDepth`。void **尾调用**路径经优化后通常不会因此类原因溢出；详见 [execution.md](execution.md)「void 尾调用（TCO）」。

---

## 解释器主流程（非 `ObrException` 消息体，审计用）

| 码 | 出现处 |
|----|--------|
| `E_IO_READ` | `ObrInterpreter#run` 捕获 `IOException` 时写入 `interpreter_error` 的 `error_code` |

---

## 无前缀 `ObrException`（节选，消息即全文）

| 场景 | 类 / 位置 |
|------|-----------|
| CLI 未知参数 | `LaunchArgs#parse` |
| 路径不存在、非 `main.obr` 文件名、多 `main.obr`、`libs` 维护失败等 | `ProjectLocator`、`LibsProvisioner` |
| char 字面量非法 | `CharLiteralParser` |
| 词法 `expect` 失败、越界 | `TokenCursor` |
| 词法无法识别字符 | `Lexer`（`err`） |
| 读 `.obr` / 扫描失败 | `ObrProgramLoader` |
| 找不到 `.mr`、扫描 `.mr` 失败、读模块失败 | `MrModuleIndex`、`ModuleLoader` |

测试与日志需匹配**完整消息**或子串；此类错误无统一 `E_*` 前缀。
