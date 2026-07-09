package example;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record YamlLibrary(String name, List<String> books) {
}
