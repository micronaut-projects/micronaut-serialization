package example;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record PropertiesLibrary(PropertiesBook book) {

    @Serdeable
    public record PropertiesBook(String title, List<Author> authors) {
    }

    @Serdeable
    public record Author(String name, int age) {
    }
}
