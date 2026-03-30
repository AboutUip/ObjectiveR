# 06 章：练习题与自测清单

导航：

- [上一章：05 `#VERSION` 与 `#LINK`](./05-VERSION-LINK-AndProjectSpace.md)
- [回到目录](./README.md)

建议你按顺序做：每做完一题，就回到前一章快速对照语法要点。

## 题 1：用 `while + ++/--` 输出 1..N

目标：

- 在 `main.obr` 里声明 `var[int] i = 1;`
- 写 `while (i <= N)` 循环
- 每轮执行 `std::rout(i);`，并更新 `i`（用 `i++;` 或 `i += 1;`）

自测检查点：

- 程序能结束（不会死循环）
- `;` 没丢

## 题 2：用三元 `?:` 把奇数/偶数分到两个和里

目标：

- 从 1 迭代到 `max`
- 偶数加到 `evenSum`，奇数加到 `oddSum`
- 仍然使用一行三元表达式语句（类似 `demo/main.obr`）

自测检查点：

- 三元表达式那一行末尾有 `;`
- 三元的两个分支都能通过类型匹配（例如都是对 `int` 做 `+=`）

## 题 3：实现并调用 `add([int]a,[int]b):int`

你需要至少两个文件：

- `math.mr`：声明 `deRfun add([int]a,[int]b):int;`
- `main.obr`：`import math`，并实现 `deRfun add(...):int{ return a+b; }`

自测检查点：

- `.mr` 里是声明（以 `;` 结尾，没有 `{}`）
- `.obr` 里是实现（有函数体 `{}`）
- `main` 能打印 `add(x,y)` 的结果

## 题 4：写一个 `abs([int]x):int`，用 `if/else` 分支

目标：

- 当 `x < 0` 时返回 `-x`
- 否则返回 `x`

自测检查点：

- `abs` 的返回类型和 `.mr` 声明一致
- `return expr;` 只出现在非 void 函数里（例如 `abs` 是 `int`）

## 题 5：在 `while` 中用 `break/continue`

目标（任选其一）：

- 遇到 `i == 8` 时 `break;`，只输出到 7
- 遇到 `i == 5` 时 `continue;`，跳过输出 5

自测检查点：

- `break;` / `continue;` 都带分号
- 它们位于 `while` 循环体内部

## 题 6（理解题）：收窄 `#LINK` 导致的访问错误应该怎么读

目标（不要求你一定一次成功跑通）：

- 创建两个 `.obr` 文件：例如 `main.obr` 与 `feature.obr`
- 在 `feature.obr` 里写一个更严格的 `#LINK`，例如只允许访问方是 `/main.obr`
- 当 `main.obr` 试图使用 `feature.obr` 的公开符号时，观察解释器给你的访问控制相关报错

自测检查点：

- `#LINK` 写在 `.obr` 里
- 路径项使用 `/` 开头的“项目根绝对逻辑路径”

