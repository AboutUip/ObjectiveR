# 数值重载消解

**实现类**：`com.kitepromiss.obr.semantic.NumericWidening`

**调用点**：

| 阶段 | 方法 |
|------|------|
| 语义 | `SemanticBinder.resolveByNumericWiden` |
| 运行时 | `RuntimeExecutor.resolveRuntimeSignature` |

**语言规范交叉引用**：`docs/obr/operators.md` §1.2（表达式）、[`docs/obr/moduleR.md`](../obr/moduleR.md) §7.9（**仅调用点**加宽；表达式与解耦）。

---

## 输入

- 限定函数名 `qn`（与 `FunctionSignature.qualifiedName()` 一致）。
- 实参静态类型关键字序列 `actualTypes`（与调用点推断一致）。

**候选**：`qualifiedName` 相同且形参个数相同的所有签名。

---

## `oneArgCost(actual, formal)`

相等 → `0`。否则仅下列数值链有非负代价（否则 `-1`）：

| actual → formal | 代价 |
|-----------------|------|
| int → long | 1 |
| int → float | 2 |
| int → double | 3 |
| long → float | 1 |
| long → double | 2 |
| float → double | 1 |

---

## `totalWideningCost(actualTypes, formalTypes)`

逐位 `oneArgCost` 求和；任一位 `-1` 则整体无效。

---

## 选择规则

1. 若存在**实参类型序列与形式参数类型序列完全一致**的键，优先使用该键（见 `SemanticBinder` / `RuntimeExecutor` 中精确匹配分支）。
2. 否则在有效候选中取 **总代价最小**。
3. 多个候选 **并列最小** → `E_SEM_OVERLOAD_AMBIGUOUS`（语义）或 `E_RT_OVERLOAD_AMBIGUOUS`（运行时）。

---

## 维护

修改代价表时须同步两处调用方与 `docs/obr/` 中数值规则；并运行 `NumericWideningTest`、语义与运行时相关测试。
