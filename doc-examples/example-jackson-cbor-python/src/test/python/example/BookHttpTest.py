from typing import Annotated

from jakarta.inject import Inject
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from example.Book import Book
from example.BookClient import BookClient


@MicronautTest
class BookHttpTest:
    client: Annotated[BookClient, Inject]

    @Test
    def cbor_http_round_trip(self):
        saved = self.client.save(Book("The Stand", 50))
        assert saved is not None
        assert saved.title == "The Stand"
        assert saved.quantity == 50
