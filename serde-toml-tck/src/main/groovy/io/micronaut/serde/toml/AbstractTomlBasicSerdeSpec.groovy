package io.micronaut.serde.toml

import io.micronaut.serde.toml.fixture.Book
import io.micronaut.serde.toml.fixture.BookHolder
import io.micronaut.serde.toml.fixture.BookList
import io.micronaut.serde.toml.fixture.SimpleBook
import io.micronaut.serde.toml.fixture.TagList
import spock.lang.Specification

abstract class AbstractTomlBasicSerdeSpec extends Specification implements TomlSpec {

    void "serializes and deserializes a simple bean"() {
        given:
        def book = new SimpleBook()
        book.title = "Micronaut in Action"
        book.pages = 320

        when:
        def toml = writeToml(book)
        def decoded = readToml(toml, SimpleBook)

        then:
        toml == "title = 'Micronaut in Action'\npages = 320\n"
        decoded.title == "Micronaut in Action"
        decoded.pages == 320
    }

    void "serializes nested beans with dotted keys"() {
        given:
        def author = new Book.Author()
        author.name = "Ada"
        def book = new Book()
        book.title = "Micronaut in Action"
        book.pages = 320
        book.author = author

        when:
        def toml = writeToml(book)
        def decoded = readToml(toml, Book)

        then:
        toml == "title = 'Micronaut in Action'\npages = 320\nauthor.name = 'Ada'\n"
        decoded.title == "Micronaut in Action"
        decoded.pages == 320
        decoded.author.name == "Ada"
    }

    void "serializes scalar arrays deterministically"() {
        given:
        def tags = new TagList()
        tags.tags = ["micronaut", "toml"]

        when:
        def toml = writeToml(tags)
        def decoded = readToml(toml, TagList)

        then:
        toml == "tags = ['micronaut', 'toml']\n"
        decoded.tags == ["micronaut", "toml"]
    }

    void "parses inline tables semantically"() {
        given:
        def toml = "book = { title = 'Micronaut in Action', pages = 320, author = { name = 'Ada' } }\n"

        when:
        def holder = readToml(toml, BookHolder)

        then:
        holder.book != null
        holder.book.title == "Micronaut in Action"
        holder.book.pages == 320
        holder.book.author.name == "Ada"
        objRepresentationMatches(holder, toml)
    }

    void "parses arrays of tables semantically"() {
        given:
        def toml = """[[books]]
title = 'Micronaut in Action'
pages = 320
author.name = 'Ada'

[[books]]
title = 'TOML in Action'
pages = 280
author.name = 'Bob'
"""

        when:
        def books = readToml(toml, BookList)

        then:
        books.books.size() == 2
        books.books[0].title == "Micronaut in Action"
        books.books[0].pages == 320
        books.books[0].author.name == "Ada"
        books.books[1].title == "TOML in Action"
        books.books[1].pages == 280
        books.books[1].author.name == "Bob"
        objRepresentationMatches(books, toml)
    }

    void "round trips object graphs semantically"() {
        given:
        def firstAuthor = new Book.Author(name: "Ada")
        def secondAuthor = new Book.Author(name: "Bob")
        def first = new Book(title: "Micronaut in Action", pages: 320, author: firstAuthor)
        def second = new Book(title: "TOML in Action", pages: 280, author: secondAuthor)
        def books = new BookList(books: [first, second])

        when:
        def decoded = roundTrip(books)

        then:
        decoded.books.size() == 2
        decoded.books*.title == ["Micronaut in Action", "TOML in Action"]
        decoded.books*.pages == [320, 280]
        decoded.books*.author*.name == ["Ada", "Bob"]
    }

    void "rejects duplicate keys"() {
        when:
        readToml("name = 'Tom'\nname = 'Pradyun'\n", Map)

        then:
        def e = thrown(Exception)
        e.message.contains("Duplicate key")
    }

    void "rejects scalar object path collisions"() {
        when:
        readToml("fruit.apple = 1\nfruit.apple.smooth = true\n", Map)

        then:
        def e = thrown(Exception)
        e.message.contains("Path into existing non-object value") || e.message.contains("Duplicate key")
    }

    void "rejects invalid array table collisions"() {
        when:
        readToml("fruit = []\nfruit.apple = 1\n", Map)

        then:
        def e = thrown(Exception)
        e.message != null
    }
}
