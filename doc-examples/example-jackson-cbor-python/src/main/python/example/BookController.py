from typing import Annotated

from micronaut.http.annotation import Body, Controller, Post
from micronaut.serde.cbor import CborMediaTypes

from example.Book import Book


@Controller("/books")
class BookController:

    @Post(processes=CborMediaTypes.APPLICATION_CBOR)
    def save(self, book: Annotated[Book, Body]) -> Book:
        return book
