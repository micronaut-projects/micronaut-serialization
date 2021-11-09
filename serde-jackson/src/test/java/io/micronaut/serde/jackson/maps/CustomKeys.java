package io.micronaut.serde.jackson.maps;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class CustomKeys {
    private final Map<CustomKey, Integer> data;

    public CustomKeys(Map<CustomKey, Integer> data) {
        this.data = data;
    }

    public Map<CustomKey, Integer> getData() {
        return data;
    }

    public static class CustomKey {
        private final String name;

        public CustomKey(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

}
