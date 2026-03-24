package com.kitepromiss.obr.semantic;

import com.kitepromiss.obr.ast.ParamDecl;
import com.kitepromiss.obr.ast.QualifiedName;

import java.util.List;

/** 函数签名判定键：限定函数名 + 参数类型序列（不含返回值）。 */
public record FunctionSignature(String qualifiedName, List<String> paramTypes) {

    public static FunctionSignature of(QualifiedName name, List<ParamDecl> params) {
        return new FunctionSignature(
                String.join("::", name.segments()),
                params.stream().map(p -> p.type().keywordLexeme()).toList());
    }

    @Override
    public String toString() {
        return qualifiedName + "(" + String.join(",", paramTypes) + ")";
    }
}
