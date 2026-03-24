package com.kitepromiss.obr.ast;

/** 函数形参/返回类型，暂存关键字词面值（如 {@code void}、{@code string}）。 */
public record TypeRef(String keywordLexeme) {}
