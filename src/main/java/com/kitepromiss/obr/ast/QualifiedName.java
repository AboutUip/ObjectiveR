package com.kitepromiss.obr.ast;

import java.util.ArrayList;
import java.util.List;

public record QualifiedName(List<String> segments) {

    public static QualifiedName of(String name) {
        return new QualifiedName(List.of(name));
    }

    public static QualifiedName join(List<String> prefix, QualifiedName tail) {
        if (prefix.isEmpty()) {
            return tail;
        }
        List<String> s = new ArrayList<>(prefix);
        s.addAll(tail.segments());
        return new QualifiedName(List.copyOf(s));
    }
}
