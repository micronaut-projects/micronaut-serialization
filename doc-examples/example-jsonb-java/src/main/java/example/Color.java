package example;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public final class Color {
    private final String value;

    public Color(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
