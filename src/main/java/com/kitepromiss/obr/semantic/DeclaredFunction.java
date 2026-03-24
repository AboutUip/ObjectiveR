package com.kitepromiss.obr.semantic;

import com.kitepromiss.obr.ast.MrItem;

/** 记录声明来源，便于错误报告。 */
public record DeclaredFunction(String moduleName, MrItem.DeRfunDecl decl) {}
