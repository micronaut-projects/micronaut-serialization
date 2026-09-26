from dataclasses import dataclass
from typing import Annotated

from com.fasterxml.jackson.annotation import JsonRootName
from micronaut.serde.annotation import Serdeable
from tools.jackson.dataformat.xml.annotation import JacksonXmlElementWrapper, JacksonXmlProperty


@Serdeable  # <1>
@JsonRootName("book")  # <2>
@dataclass
class Book:
    isbn: Annotated[str, JacksonXmlProperty(isAttribute=True, localName="isbn")]  # <3>
    title: Annotated[str, JacksonXmlProperty(localName="title")]  # <4>
    authors: Annotated[
        list[str],
        JacksonXmlElementWrapper(localName="authors"),  # <5>
        JacksonXmlProperty(localName="author"),  # <6>
    ]
