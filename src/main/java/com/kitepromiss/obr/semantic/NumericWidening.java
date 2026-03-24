package com.kitepromiss.obr.semantic;

import java.util.List;

/**
 * <p>数值型实参到形参的「加宽」代价（与 {@link FunctionSignature} 的限定名 + 参数类型序列配合使用）。
 * 语义分析阶段（{@code SemanticBinder}）与运行时（{@code RuntimeExecutor}）对<strong>同一套</strong>规则求值，
 * 避免两处手写分支漂移。</p>
 *
 * <h2>设计要点（对应规范中的隐式数值转换）</h2>
 * <ul>
 *   <li>仅覆盖<strong>数值类型</strong>链：{@code int} → {@code long} → {@code float} → {@code double}；
 *       {@code byte}、{@code char}、{@code string}、{@code boolean} 等不参与本表的加宽匹配（须精确相等或另行规则）。</li>
 *   <li>对<strong>每个参数位置</strong>独立计算「一步或多步加宽」的代价并<strong>求和</strong>；
 *       总代价越小，表示「离静态类型越近」的候选越优先。</li>
 *   <li>若某一位置无法从实参类型加宽到形参类型，该候选整体<strong>无效</strong>（返回负总分）。</li>
 *   <li>在<strong>总分相同</strong>的多个候选之间，视为<strong>二义性</strong>，须报错（语义与运行时各自抛出带前缀的错误码）。</li>
 * </ul>
 *
 * <h2>单参数代价表（与历史实现一致）</h2>
 * <pre>
 *   相等           → 0
 *   int→long       → 1
 *   int→float      → 2
 *   int→double     → 3
 *   long→float     → 1
 *   long→double    → 2
 *   float→double   → 1
 * </pre>
 *
 * <h2>与「重载消解」的关系</h2>
 * <p>本类<strong>不</strong>解析限定函数名；调用方在已过滤「同名、同参数个数」的候选集合上，
 * 用 {@link #totalWideningCost(List, List)} 比较总分并处理并列。详见仓库内 {@code docs/blink/overload-resolution.md}。</p>
 */
public final class NumericWidening {

    private NumericWidening() {}

    /**
     * @return 非负总代价；若任一参数位置无法加宽则返回 -1
     */
    public static int totalWideningCost(List<String> actualTypes, List<String> formalTypes) {
        if (actualTypes.size() != formalTypes.size()) {
            return -1;
        }
        int sum = 0;
        for (int i = 0; i < actualTypes.size(); i++) {
            int c = oneArgCost(actualTypes.get(i), formalTypes.get(i));
            if (c < 0) {
                return -1;
            }
            sum += c;
        }
        return sum;
    }

    /**
     * @return 0 表示精确匹配；正数表示加宽步数代价；-1 表示不兼容
     */
    public static int oneArgCost(String actualTypeKeyword, String formalTypeKeyword) {
        if (actualTypeKeyword.equals(formalTypeKeyword)) {
            return 0;
        }
        return switch (actualTypeKeyword) {
            case "int" -> switch (formalTypeKeyword) {
                case "long" -> 1;
                case "float" -> 2;
                case "double" -> 3;
                default -> -1;
            };
            case "long" -> switch (formalTypeKeyword) {
                case "float" -> 1;
                case "double" -> 2;
                default -> -1;
            };
            case "float" -> "double".equals(formalTypeKeyword) ? 1 : -1;
            default -> -1;
        };
    }
}
