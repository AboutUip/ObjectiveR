# ObjectiveR 规范总览

## 状态

- 当前状态：草案（Draft）
- 适用范围：ObjectiveR 解释器及其对应语言版本

## 基础约定

- 源文件扩展名为 `.obr`
- 语言为解释执行
- 解释器由 Java 实现（原生支持跨平台运行）
- 语言为强类型
- 语言采用严格大小写检查（Case-Sensitive）
- 各版本之间默认互不兼容

## 版本策略

- 不同版本支持的语法与预处理指令集合是确定的。
- 解释器应仅接受其支持版本的语言特性。
- 文件若未显式声明版本，默认按当前解释器可解释执行的 Obr 版本处理。

## 规范分层

- 预处理指令规则见 `preprocessor.md`
- 启动输入、唯一 `main.obr` 约束、根目录判定、资源扫描与执行入口规则见 `runtime.md`
- 可见性与访问修饰规则见 `visibility.md`
- `static` 关键字规则见 `static.md`
- 语句终止与换行规则见 `termination.md`
- `.mr`（moduleR）文件与 `import` 导入规则见 `moduleR.md`
- 注释规则见 `comments.md`
- 运算符与隐性转化规则见 `operators.md`
- 作用域与标识符解析规则见 `scope.md`
- 基础数据类型规则见 `types.md`

## 已确认约束

- 项目空间由 `main.obr` 的语言空间唯一决定，与启动目录无关。
