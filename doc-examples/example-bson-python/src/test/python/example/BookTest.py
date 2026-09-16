from typing import Annotated

from jakarta.inject import Inject
from micronaut.serde import ObjectMapper
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from example.Book import Book


@MicronautTest
class BookTest:
    object_mapper: Annotated[ObjectMapper, Inject]

    @Test
    def test_write_read_book(self):
        result = self.object_mapper.writeValueAsString(Book("The Stand", 50))

        book = self.object_mapper.readValue(result, Book)
        assert book is not None
        assert book.title == "The Stand"
        assert book.quantity == 50
