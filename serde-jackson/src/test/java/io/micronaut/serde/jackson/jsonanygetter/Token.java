package io.micronaut.serde.jackson.jsonanygetter;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

@Introspected
public class Token {

    @NonNull
    private final Map<String, Object> extensions = new HashMap<>();

    public Token(@Nullable Map<String, Object> extensions) {
        if (extensions != null) {
            this.extensions.putAll(extensions);
        }
    }

    /**
     *
     * @return Extensions
     */
    @JsonAnyGetter
    public Map<String, Object> getExtensions() {
        return extensions;
    }
}
