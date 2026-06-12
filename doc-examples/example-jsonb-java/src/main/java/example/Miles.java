package example;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public final class Miles {
    private final int value;

    public Miles(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
