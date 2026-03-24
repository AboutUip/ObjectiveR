# `#VERSION` 校验（`com.kitepromiss.obr.semantic.VersionDirectiveChecker`）

**入口**：`VersionDirectiveChecker#checkProgram(ObrProgramBundle)`：对每个 `ParsedObrFile` 调用 `checkObrFile(path, ast)`。

---

## 扫描规则

- 仅遍历 `ObrItem` 中 **`Preproc`**（顶层预处理行 token 文本）。
- `trim` 后行首为 `#`，且去掉 `#` 后 `trim` 以 `VERSION` 开头 → 参与版本解析。
- 正则：`^#\\s*VERSION\\s+([0-9]+)\\s*$`（`VERSION_LINE`）。  
  不匹配 → `E_PREPROC_VERSION_PARSE`。
- 解析出整数 `< 1` → `E_PREPROC_VERSION_PARSE`。

---

## 单文件内一致性

- 收集所有解析成功的版本号列表；若多条且不全相等 → `E_VER_MISMATCH`。

---

## 与解释器支持版本

- 取首条（或唯一）版本号 `first`；与 `ObrLanguageVersion.SUPPORTED` 比较。  
- 不等 → `E_VER_UNSUPPORTED`。

---

## 无 `#VERSION` 行

- 若该文件无有效 `#VERSION` 行 → **直接返回**（不报错）。

---

## 错误码

见 [errors.md](errors.md)：`E_PREPROC_VERSION_PARSE`、`E_VER_MISMATCH`、`E_VER_UNSUPPORTED`。
