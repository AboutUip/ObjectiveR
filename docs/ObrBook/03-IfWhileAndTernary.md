# 03 章：分支、循环与三元运算（把“能算”变成“能走”）

导航：

- [上一章：02 变量、赋值与表达式](./02-VariablesAndExpressions.md)
- [下一章：04 模块与函数](./04-ModulesMRAndFunctions.md)

这一章你将学会：

- `if / else` 写法（建议先用 `{ ... }`）
- `while` 循环与循环体作用域
- `break` / `continue`
- 三元运算符 `?:`：`Cond ? Expr1 : Expr2`

## 3.1 `if / else`：用 `{ ... }` 最稳

推荐写法：

```text
if (cond) {
    // then 分支
} else {
    // else 分支
}
```

你可以把比较表达式直接塞进条件里，例如：

```text
if (x == 0) {
    std::rout("zero");
} else {
    std::rout("not zero");
}
```

### 3.1.1 `else if` 链（你会用得很频繁）

```text
if (x < 0) {
    std::rout("negative");
} else if (x == 0) {
    std::rout("zero");
} else {
    std::rout("positive");
}
```

## 3.2 `while`：循环条件与循环体

语法是：

```text
while (Cond) {
    // 循环体
}
```

循环体是“块作用域”：在 `{ ... }` 里声明的局部变量不会泄漏到外面。

## 3.3 `break` / `continue`

- `break;`：直接跳出最近一层 `while`
- `continue;`：跳过本轮剩余部分，进入下一轮

示例：

```text
while (i < 10) {
    i++;

    if (i == 5) {
        continue; // i==5 时不做下面的 std::rout
    }

    if (i == 8) {
        break; // i==8 时直接结束 while
    }

    std::rout(i);
}
```

## 3.4 三元运算 `?:`：让表达式也能分支

语法：

```text
CondQ ? Expr1 : Expr2
```

在条件运算中，`CondQ` 会被当作“布尔语义”来判断。

### 3.4.1 一个最符合直觉的例子

我们用最常见的“奇偶分流”：

```text
if (i % 2 == 0) {
    sum += i;
} else {
    rum += i;
}
```

把它改写成三元表达式（作为表达式语句使用）：

```text
i % 2 == 0 ? sum += i : rum += i;
```

注意：这一行最后仍然要写 `;`（它是“表达式语句”）。

## 3.5 一份完整示例（建议你直接跑）

这段代码的写法基本就是你 `demo/main.obr` 的教学版：

```text
#VERSION 1
#LINK /

deRfun main():void{
    var[int] min = 0,
             max = 20;

    // 偶数和 / 奇数和
    var[int] sum = 0,
             rum = 0;

    // 让 min 从 1 开始更直观（也可以删掉这两行）
    min--;

    while (min <= max) {
        min++;

        // 三元：偶数加到 sum，奇数加到 rum
        min % 2 == 0 ? sum += min : rum += min;
    }

    std::rout("even=" + sum);
    std::rout("odd=" + rum);
}
```

## 3.6 常见错误（只列你最可能犯的）

- 在三元表达式末尾忘记写分号：`... : ...;`
- 在 `if/while` 的条件里写了“不能变成布尔语义”的东西（零基础阶段建议永远用 `x == y` / `x < y` 这种比较）

