package com.kitepromiss.obr.semantic;

/**
 * 解释器当前实现并支持的 Obr 语言版本（与 {@code #VERSION} 及 {@code docs/obr/preprocessor.md} 一致）。
 */
public final class ObrLanguageVersion {

    /** 当前唯一支持的版本号；语言完结前规范与实现均保持为 1。 */
    public static final int SUPPORTED = 1;

    private ObrLanguageVersion() {}
}
