from typing import Annotated

from micronaut.http.annotation import Body, Controller, Post
from micronaut.serde.yaml import YamlMediaTypes

from example.Book import Book


@Controller
class YamlBookController:

    @Post(uri="/books", processes=YamlMediaTypes.APPLICATION_YAML)
    def save(self, book: Annotated[Book, Body]) -> Book:
        return book
