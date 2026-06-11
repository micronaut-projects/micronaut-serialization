package example;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public final class ProgrammaticCode {
    private final String value;

    public ProgrammaticCode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
