package com.kitepromiss.obr.ast;

import java.util.List;

public sealed interface ObrItem permits ObrItem.Preproc, ObrItem.Import, ObrItem.DeRfunDef {

    record Preproc(String rawLine) implements ObrItem {}

    record Import(String moduleName) implements ObrItem {}

    record DeRfunDef(QualifiedName name, List<ParamDecl> params, TypeRef returnType, BlockStmt body)
            implements ObrItem {}
}
