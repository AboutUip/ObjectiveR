# 04 章：模块与函数（理解为什么要 `.mr`）

导航：

- [上一章：03 分支、循环与三元运算](./03-IfWhileAndTernary.md)
- [下一章：05 `#VERSION` 与 `#LINK`](./05-VERSION-LINK-AndProjectSpace.md)

这一章你将学会：

- `.mr` 文件“只声明不实现”的规则
- `.obr` 中实现函数必须与 `.mr` 中的函数头匹配
- `import xxx` 如何把 `xxx.mr` 加载进来
-（可选）`namespace` 与限定名 `::` 的调用/实现方式

## 4.1 先搞清楚：`.mr` 是“合同”，`.obr` 是“工厂生产”

在 Obr 中：

- `.mr`：写函数头声明（`deRfun ...;`），不写函数体
- `.obr`：写函数实现（`deRfun ... : ... { ... }`），函数体在这里

解释器的语义检查会要求：**实现必须能找到唯一匹配的函数头声明**。所以你不能只写实现就完事。

## 4.2 最小例子：在 `.mr` 里声明 `add`，在 `.obr` 里实现并调用

### 4.2.1 新建 `math.mr`

放在项目里一个 `math.mr`（文件名严格匹配模块名 `math`）：

```text
deRfun add([int]a,[int]b):int;
```

注意点：

- 末尾是分号 `;`
- 没有函数体（没有 `{ ... }`）

### 4.2.2 在 `main.obr` 里写实现与调用

`main.obr` 示例：

```text
#VERSION 1
#LINK /

import math

deRfun main():void{
    var[int] x = 1,
             y = 2;
    std::rout(add(x, y));
}

deRfun add([int]a,[int]b):int{
    return a + b;
}
```

这里发生了两件匹配关系：

1. `import math` 让解释器加载并解析 `math.mr`
2. `deRfun add(...):int{ ... }` 在实现阶段，会去 `math.mr` 里找匹配的函数头

## 4.3 匹配规则（你必须对齐）

`.mr` 里的函数头与 `.obr` 里的函数实现之间，至少要对齐这些信息：

- 函数名（限定名也算名）
- 参数个数
- 参数类型序列（声明顺序）
- 返回类型

只要这些不一致，就无法完成绑定。

## 4.4 命名空间：`namespace ... { ... }` 与 `::`

命名空间只允许写在 `.mr` 中（零基础阶段你只记结论即可）：

- `.mr`：可以写 `namespace xxx { ... }`
- `.obr`：不能写 `namespace xxx { ... }`
- 如果函数头在 `.mr` 里属于 `namespace math`，那么 `.obr` 的实现与调用都必须使用限定名 `math::...`

示例（同样写一个 `math::add`）：

`math.mr`：

```text
namespace math {
    deRfun add([int]a,[int]b):int;
}
```

`main.obr`：

```text
import math

// 调用时要用限定名
std::rout(math::add(x, y));

// 实现时也要用限定名
deRfun math::add([int]a,[int]b):int{
    return a + b;
}
```

## 4.5 常见错误（本章高频）

- 在 `.mr` 里写 `{ ... }` 函数体：不允许（`.mr` 只能声明）
- `.mr` 的函数头结尾忘记写 `;`
- `.obr` 里的实现签名（参数类型/返回类型）与 `.mr` 不一致
- `import` 写进函数体里：它应该写在 `.obr` 的最外层

