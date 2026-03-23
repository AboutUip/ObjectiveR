# docs

该目录用于集中维护 ObjectiveR（Obr）文档，尤其是语言规范。

## 目前内容

- `spec/README.md`：语言规范索引
- `spec/overview.md`：语言规范总览
- `spec/preprocessor.md`：预处理指令规范
- `LANGUAGE_SPEC.md`：兼容入口（重定向到 `spec`）

## 维护约定

- 语言语法、语义、类型系统等正式定义统一收敛到 `docs` 目录
- 规范采用“总览 + 专题”多文档拆分维护
- 新增语言特性时，优先更新对应专题文档，再更新实现与示例
