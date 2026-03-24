package com.kitepromiss.obr.ast;

/**
 * 局部变量上的可见性：仅在与 {@code static} 同现时有意义；普通 {@code var} 为 {@link #LOCAL}。
 */
public enum VarVisibility {
    /** 非 static 的块局部变量（禁止写 public/private）。 */
    LOCAL,
    /** {@code static var} 或 {@code public static var}，默认视为 public。 */
    PUBLIC_STATIC,
    /** {@code private static var}。 */
    PRIVATE_STATIC
}
