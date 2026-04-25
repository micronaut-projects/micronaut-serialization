package io.micronaut.serde.toml

import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Named
import spock.lang.Specification

@MicronautTest
class TomlObjectMapperSpec extends Specification {

    @Inject
    @Named("toml")
    ObjectMapper tomlMapper

    void "serializes and deserializes a simple bean"() {
        given:
        def author = new Book.Author()
        author.name = "Ada"
        def book = new Book()
        book.title = "Micronaut in Action"
        book.pages = 320
        book.author = author

        when:
        String toml = tomlMapper.writeValueAsString(book)
        println toml + "+++++"
        Book decoded = tomlMapper.readValue(toml, Book)

        then:
        toml.contains("title = 'Micronaut in Action'")
        toml.contains('pages = 320')
        toml.contains("author.name = 'Ada'")
        decoded.title == "Micronaut in Action"
        decoded.pages == 320
        decoded.author.name == "Ada"
    }
}
