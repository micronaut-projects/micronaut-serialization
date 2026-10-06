package example;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record ToonBook(String title, List<String> authors) {
}
