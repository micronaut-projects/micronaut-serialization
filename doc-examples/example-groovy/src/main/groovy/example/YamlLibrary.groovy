package example

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import io.micronaut.serde.annotation.Serdeable

@Serdeable
@EqualsAndHashCode
@ToString
class YamlLibrary {
    final String name
    final List<String> books

    YamlLibrary(String name, List<String> books) {
        this.name = name
        this.books = books
    }
}
