# ObjectiveR 系统头 `system.mr` 与 `libs/` 目录规范

**相关**：[执行模型](runtime.md) · [moduleR](moduleR.md) · [import 规则](moduleR.md#import-导入规则)

## 1. `system.mr`（系统头文件）

- 系统头文件在语言层命名为 **`system.mr`**，由解释器提供语义与（或）磁盘内容；定义入口函数头等系统级 `deRfun` 声明。
- 任意 `.obr` 文件在语义上**均依赖** `system.mr` 所声明的符号（至少包含入口 `main` 的函数头）。
- 允许在源码中显式书写 `import system`；**即使未书写**，解释器也必须在加载任意 `.obr` 时**等价于已导入** `system.mr`（隐式导入），以保证行为一致、避免遗漏。

### 1.1 标准命名空间 `std` 与 `std::rout`

- 解释器提供并维护的 **`system.mr`** 中**必须**包含 **`namespace std { ... }`**，用于承载标准库式符号；与项目内其它已加载 `.mr` 中的 `namespace std` 按 [moduleR 第 6.3.2 节](moduleR.md) **合并**为同一 `std` 视图。
- **`std::rout`**：在 `namespace std` 内声明控制台文本输出，典型函数头如下（参数名 `out` 为 `string` 类型，语义为待输出内容）：

```text
namespace std {
    @Overwrite(libs/system.obr)
    deRfun rout([string]out):void;
    @Overwrite(libs/system.obr)
    deRfun rout([byte]out):void;
    @Overwrite(libs/system.obr)
    deRfun rout([short]out):void;
    @Overwrite(libs/system.obr)
    deRfun rout([int]out):void;
    @Overwrite(libs/system.obr)
    deRfun rout([long]out):void;
    @Overwrite(libs/system.obr)
    deRfun rout([char]out):void;
    @Overwrite(libs/system.obr)
    deRfun rout([float]out):void;
    @Overwrite(libs/system.obr)
    deRfun rout([double]out):void;
}
```

语义与约束（强制）：

- **用途**：将实参**写出到控制台**（标准输出）。**`[string]`**：文本。**`[byte]` / `[short]` / `[int]` / `[long]`**：十进制整数。**`[char]`**：单字符文本。**`[float]` / `[double]`**：十进制浮点（与 `**` 等表达式类型衔接）。具体 IO 模型由解释器实现，但不得改变「实参 → 控制台输出」的契约。
- **实现归属**：**仅**允许由 **`libs/system.obr`** 提供该函数头的实现；用户编写的任意其它 `.obr` **不得**包含 `deRfun std::rout(...)` 定义。解释器可将该实现实现为**原生/托管代理**（例如 Java 代理绑定），而不必要求用户手写 Obr 源码，但语义上仍视为绑定到上述函数头与 `@Overwrite` 路径。
- **调用**：未写 `@Callfun` 时，默认等价于 `@Callfun(*)`（见 [moduleR 第 7.4 节](moduleR.md)），即**允许任意** `.obr` 在用户代码路径中调用 `std::rout(...)`（在可见性与语言空间规则允许的前提下）。

## 2. 项目根目录下的 `libs/` 目录

在 [`main.obr`](runtime.md) 已定位且**项目根目录**已确定之后：

- 解释器必须在项目根目录下使用目录 **`libs/`**，用于存放**仅由解释器管理**的 `.mr` 文件。
- **用户自行编写的 `.mr` 不得**放入 `libs/`；用户 moduleR 应置于项目内其它路径，由 `import` 解析规则处理。
- **同名禁止**：若项目内其它路径存在与 `libs/` 中托管模块**同名**的 `xxx.mr`，必须报错（见 [moduleR 第 5.4 节](moduleR.md)）。

### 2.1 `libs/` 固定托管清单（规范写死 · **3B**）

本规范版本对 `libs/` 内解释器托管文件采用**固定最小集合**，解释器**不得**在 `libs/` 中增加、删除或替换为未列于下列清单的文件名（若未来需扩展，须修订本规范）：

| 文件名 | 说明 |
|--------|------|
| `system.mr` | 系统头，含 `main`、`namespace std` 与 `std::rout` 等声明 |
| `system.obr` | 系统实现载体，**仅**用于实现 `system.mr` 中要求由系统实现的函数头（如 `std::rout`）；由解释器维护或代理，用户不得在其它的 `.obr` 中重复实现上述函数头 |

- 解释器必须保证 `libs/` 中存在上述全部文件；缺失则须**补全或重建**。
- 解释器**不得**在 `libs/` 中写入上表以外的托管 `.mr` 文件名。

### 2.2 存在性校验与补全

- 若项目根下**已存在** `libs/`：解释器必须先**校验**其内容是否**恰好**满足第 2.1 节清单（文件齐全且无多余未列名文件）。
- 若校验发现缺失或多余：解释器必须**重建或修正** `libs/`，使其与第 2.1 节一致。
- 若项目根下**不存在** `libs/`：解释器必须**创建** `libs/` 并写入第 2.1 节所列文件。

**实现等价策略**：允许在一次运行开始前对已有 `libs/` 做**整目录删除后重建**（再写入清单内文件），视为满足「校验并修正」；运行结束后删除整个 `libs/` 目录、下次再重建，亦视为不违背「清单与内容由解释器保证」的语义（见 [执行模型](runtime.md) §3.1）。

### 2.3 解释器对托管文件的发现与发布

- 在加载任意 `.obr`、解析隐式 `system.mr` 或进入项目语义流程**之前**，解释器必须**发现**项目根下是否存在符合第 2.1 节清单的 `libs/` 及其中文件；缺失或不符合时，必须按第 2.2 节**补全或重建**。
- 上述行为等价于将规范所要求的 **`system.mr`、`system.obr` 等托管资源发布到用户工程**（落盘为 `libs/` 下文件，或提供与落盘**等价**的虚拟资源视图），**不得**要求用户手工维护这些文件才能通过语义检查；若实现校验文件内容，则与规范不一致的内容必须报错。

## 3. 与 `import` 的关系

- 对 `system.mr`：`import system` 为可选显式写法；隐式导入与显式导入必须解析为**同一** `system.mr` 模块实例语义（见 [moduleR 导入与状态](moduleR.md#8-导入绑定与-mr-状态共享)）。
- `import` 解析必须遵守 [moduleR 第 5.4 节](moduleR.md) 的同名约束；用户模块不得与 `libs/` 托管清单产生同名 `.mr` 冲突。
