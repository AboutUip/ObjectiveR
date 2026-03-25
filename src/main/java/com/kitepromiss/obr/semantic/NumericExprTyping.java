package com.kitepromiss.obr.semantic;

/**
 * 表达式内二元数值的类型提升（与 {@code docs/obr/operators.md} §1.2 一致；{@code byte} 与其它数值不得混用）。
 */
public final class NumericExprTyping {

    private NumericExprTyping() {}

    public static boolean isByte(String t) {
        return "byte".equals(t);
    }

    /** 一侧为 {@code byte}、另一侧为其它数值类型（非同为 byte）→ 非法。 */
    public static boolean illegalByteMix(String t1, String t2) {
        if (isByte(t1) && isByte(t2)) {
            return false;
        }
        return isByte(t1) || isByte(t2);
    }

    public static boolean isNumericPrimitive(String t) {
        return switch (t) {
            case "byte", "short", "int", "long", "float", "double" -> true;
            default -> false;
        };
    }

    /**
     * 非 {@code byte} 数值的二元算术公共类型（{@code short+int}→{@code int} 等）。
     */
    public static String promotedArithmeticType(String t1, String t2) {
        if (illegalByteMix(t1, t2)) {
            throw new IllegalArgumentException("byte mix");
        }
        if (t1.equals(t2)) {
            return t1;
        }
        if ("double".equals(t1) || "double".equals(t2)) {
            return "double";
        }
        if ("float".equals(t1) || "float".equals(t2)) {
            return "float";
        }
        if ("long".equals(t1) || "long".equals(t2)) {
            return "long";
        }
        if ("int".equals(t1) || "int".equals(t2)) {
            return "int";
        }
        if ("short".equals(t1) || "short".equals(t2)) {
            return "int";
        }
        throw new IllegalArgumentException(t1 + " " + t2);
    }

    /** {@code **}：非 byte-byte 时静态结果为 {@code double}（JS Number）。 */
    public static String powResultType(String t1, String t2) {
        if (isByte(t1) && isByte(t2)) {
            return "byte";
        }
        if (illegalByteMix(t1, t2)) {
            throw new IllegalArgumentException("byte mix");
        }
        return "double";
    }
}
