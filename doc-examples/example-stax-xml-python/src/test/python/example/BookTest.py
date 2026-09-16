from typing import Annotated

from jakarta.inject import Inject, Named
from micronaut.serde import ObjectMapper
from micronaut.serde.xml import XmlObjectMapper
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Disabled, Test

from example.Book import Book
from example.JaxbBook import JaxbBook


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

    @Disabled("TODO(python): JAXB field access (@XmlRootElement without a dataclass) yields no properties, see DISABLED_TESTS.md")
    @Test
    def test_write_read_jaxb_book(self):
        input = JaxbBook()
        input.isbn = "978-0307743688"
        input.title = "The Stand"
        input.authors = ["Stephen King"]

        result = self.xml_mapper.writeValueAsString(input)

        assert result == (
            '<book isbn="978-0307743688"><title>The Stand</title>'
            '<subtitle xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:nil="true"></subtitle>'
            '<author>Stephen King</author></book>'
        )

        book = self.xml_mapper.readValue(result, JaxbBook)
        assert book.isbn == input.isbn
        assert book.title == input.title
        assert book.subtitle == "Untitled"
        assert list(book.authors) == input.authors
