from typing import Annotated

from jakarta.inject import Inject, Named
from micronaut.serde import ObjectMapper
from micronaut.serde.xml import XmlObjectMapper
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from example.Book import Book


@MicronautTest
class BookTest:
    xml_mapper: Annotated[ObjectMapper, Inject, Named(XmlObjectMapper.XML_MAPPER_NAME)]

    @Test
    def test_write_read_book(self):
        result = self.xml_mapper.writeValueAsString(Book(
            "978-0307743688",
            "The Stand",
            ["Stephen King"],
        ))

        assert result == (
            '<book isbn="978-0307743688"><title>The Stand</title>'
            '<authors><author>Stephen King</author></authors></book>'
        )

        book = self.xml_mapper.readValue(result, Book)
        assert book is not None
        assert book.isbn == "978-0307743688"
        assert book.title == "The Stand"
        assert list(book.authors) == ["Stephen King"]
