package io.micronaut.serde.jackson.mixin;

import java.util.LinkedHashMap;
import java.util.Map;

public class AnyExtBase<T> {
    private Map<String, Object> extensions;
    private T name;

    public T getName() {
        return name;
    }

    public void setName(T name) {
        this.name = name;
    }

    public Map<String, Object> getExtensions() {
        return extensions;
    }

    public void addExtension(String key, Object value) {
        if (extensions == null) {
            extensions = new LinkedHashMap<>();
        }
        extensions.put(key, value);
    }
}
