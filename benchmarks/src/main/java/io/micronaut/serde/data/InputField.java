package io.micronaut.serde.data;

import io.micronaut.core.annotation.Introspected;

import java.util.List;

@Introspected(accessKind = Introspected.AccessKind.FIELD)
public class InputField {

    public List<String> haystack;
    public String needle;
}
