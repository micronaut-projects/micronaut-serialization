package io.micronaut.serde.jackson.maps;

import java.util.Objects;

public class CustomKey {
    private final String name;

    public CustomKey(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CustomKey customKey = (CustomKey) o;
        return name.equals(customKey.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
