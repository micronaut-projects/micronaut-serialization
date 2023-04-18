package io.micronaut.serde.data;

import io.micronaut.core.annotation.Introspected;

@Introspected(accessKind = Introspected.AccessKind.FIELD)
public class StringArrayField {
    public String[] strs;
}
