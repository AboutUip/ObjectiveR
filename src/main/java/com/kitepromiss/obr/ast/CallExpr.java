package com.kitepromiss.obr.ast;

import java.util.List;

public record CallExpr(QualifiedName callee, List<Expr> arguments) {}
