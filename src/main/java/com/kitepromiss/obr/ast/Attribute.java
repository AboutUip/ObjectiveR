package com.kitepromiss.obr.ast;

/** {@code @Name( ... )} 括号内原文（可为空）。 */
public record Attribute(String name, String rawInsideParens) {}
