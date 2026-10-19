package example;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record ToonLibrary(String name, List<ToonBook> books) {
}
