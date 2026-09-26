from abc import ABC, abstractmethod
from typing import Annotated

from micronaut.http.annotation import Body, Post
from micronaut.http.client.annotation import Client
from micronaut.serde.cbor import CborMediaTypes

from example.Book import Book


@Client("/books")
class BookClient(ABC):

    @Post(processes=CborMediaTypes.APPLICATION_CBOR)
    @abstractmethod
    def save(self, book: Annotated[Book, Body]) -> Book:
        pass
