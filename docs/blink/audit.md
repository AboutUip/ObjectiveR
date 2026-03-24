# 审计日志（`InterpreterAuditLog`）

**接口**：`com.kitepromiss.obr.trace.InterpreterAuditLog#event(TraceLevel, TraceCategory, String phase, Map fields)`。

**策略**：`LaunchArgs#tracePolicy` → `TraceLevel`；`TraceLevel#emits` 决定是否输出。**`OFF`** 时不输出解释过程事件；**错误仍走 `stderr`**。

**输出格式**：`DefaultInterpreterAuditLog` 单行：`ts=`、`level=`、`cat=`、`phase=`、键值字段。

---

## `TraceCategory` 枚举

`BOOT`、`FILES`、`MODULES`、`LEX`、`PARSE`、`SEMANTIC`、`RUNTIME`、`INTERNAL`（见源码 `TraceCategory.java`）。

---

## `ObrInterpreter#run` 中的 `phase`（主路径）

| phase | TraceCategory | TraceLevel（调用处） |
|-------|---------------|----------------------|
| `interpreter_start` | BOOT | SUMMARY |
| `project_resolved` | FILES | NORMAL |
| `libs_ensured` | MODULES | NORMAL |
| `lex_file_begin` | LEX | NORMAL |
| `lex_file_end` | LEX | NORMAL |
| `parse_main_obr_begin` | PARSE | NORMAL |
| `parse_main_obr_end` | PARSE | NORMAL |
| `obr_program_loaded` | FILES | NORMAL |
| `bind_program_begin` | SEMANTIC | NORMAL |
| `module_bundle_ready` | MODULES | NORMAL |
| `bind_program_end` | SEMANTIC | NORMAL |
| `runtime_main_begin` | RUNTIME | NORMAL |
| `runtime_main_end` | RUNTIME | NORMAL |
| `interpreter_exit` | BOOT | SUMMARY |
| `interpreter_error` | BOOT | SUMMARY |

---

## `Lexer`（逐 token，**VERBOSE**）

| phase | TraceCategory | TraceLevel |
|-------|---------------|------------|
| `token` | LEX | VERBOSE |

字段：`file`、`kind`、`lexeme`、`line`、`col`。

---

## `ObrProgramLoader`

| phase | TraceCategory |
|-------|---------------|
| `obr_loaded` | FILES |

---

## `ModuleLoader#load`

| phase | TraceCategory |
|-------|---------------|
| `module_index_built` | MODULES |
| `mr_load_begin` | MODULES |
| `mr_load_end` | MODULES |

---

## `SemanticBinder`

| phase | TraceCategory |
|-------|---------------|
| `bind_def_resolve` | SEMANTIC |
| `bind_call_resolve` | SEMANTIC |

---

## `RuntimeExecutor#call`

| phase | TraceCategory |
|-------|---------------|
| `call_resolve` | RUNTIME |
| `call_enter` | RUNTIME |
| `call_exit` | RUNTIME |

---

## `LibsProvisioner`

| phase | 说明 |
|-------|------|
| `libs_replace_existing` | 已存在 `libs/` 时删前 |
| `libs_cleanup_begin` / `libs_cleanup_done` | `cleanup` 删除目录 |

---

## 完整列表

以 `grep audit.event` 对 `src/main/java/com/kitepromiss/obr` 为准。
