package io.micronaut.serde.data;

import io.micronaut.core.annotation.Introspected;

import java.util.List;

@Introspected
public class InputSetter {

    private List<String> haystack;
    private String needle;

    public List<String> getHaystack() {
        return haystack;
    }

    public void setHaystack(List<String> haystack) {
        this.haystack = haystack;
    }

    public String getNeedle() {
        return needle;
    }

    public void setNeedle(String needle) {
        this.needle = needle;
    }
}
