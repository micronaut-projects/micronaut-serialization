package example;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record Item(int id, String name, int count) {
}
