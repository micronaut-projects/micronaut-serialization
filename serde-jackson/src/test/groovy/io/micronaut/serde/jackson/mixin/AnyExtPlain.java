package io.micronaut.serde.jackson.mixin;

import java.util.LinkedHashMap;
import java.util.Map;

public class AnyExtPlain {
    private Map<String, Object> extensions;
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
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
