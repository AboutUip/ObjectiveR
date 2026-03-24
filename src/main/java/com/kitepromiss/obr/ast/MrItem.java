package com.kitepromiss.obr.ast;

import java.util.List;

public sealed interface MrItem permits MrItem.DeRfunDecl, MrItem.Namespace {

    /** {@code name} 为合并命名空间前缀后的限定名（如 {@code std::test}）。 */
    record DeRfunDecl(List<Attribute> attributes, QualifiedName name, List<ParamDecl> params, TypeRef returnType)
            implements MrItem {}

    /** 单个 {@code namespace id { ... }} 段；可嵌套。 */
    record Namespace(String name, List<MrItem> members) implements MrItem {}
}
