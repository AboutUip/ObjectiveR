package com.kitepromiss.obr.ast;

/** {@code var[type] name (= expr)?} 中的单个声明子。 */
public record VarDeclarator(String name, Expr initOrNull) {}
