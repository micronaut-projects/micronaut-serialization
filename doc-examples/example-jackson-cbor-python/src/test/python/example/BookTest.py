from typing import Annotated

from jakarta.inject import Inject
from micronaut.serde.cbor import CborObjectMapper
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from example.Book import Book


@MicronautTest
class BookTest:
    cbor_object_mapper: Annotated[CborObjectMapper, Inject]

    @Test
    def test_write_read_book(self):
        data = self.cbor_object_mapper.writeValueAsBytes(Book("The Stand", 50))
        assert data is not None
        assert len(data) > 0
        # CBOR map major type, not JSON text
        assert (data[0] & 0xE0) == 0xA0

        book = self.cbor_object_mapper.readValue(data, Book)
        assert book is not None
        assert book.title == "The Stand"
        assert book.quantity == 50
