from typing import Annotated

from jakarta.inject import Inject
from java.lang import String
from micronaut.core.type import Argument
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

    @Test
    def test_list_of_books(self):
        result = self.object_mapper.writeValueAsString([
            Book("The Stand", 50),
            Book("Godfather", 10),
            Book("VALIS", 100),
        ])

        books = self.object_mapper.readValue(result, Argument.listOf(Book))
        assert len(books) == 3
        first_book = books[0]
        assert first_book.title == "The Stand"
        assert first_book.quantity == 50

    @Test
    def test_map_of_books(self):
        result = self.object_mapper.writeValueAsString({
            "myBook": Book("The Stand", 50),
            "hisBook": Book("Godfather", 10),
            "herBook": Book("VALIS", 100),
        })

        books = self.object_mapper.readValue(result, Argument.mapOf(String, Book))
        assert len(books) == 3
        her_book = books["herBook"]
        assert her_book.title == "VALIS"
        assert her_book.quantity == 100
