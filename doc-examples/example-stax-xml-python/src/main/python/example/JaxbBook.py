from typing import Annotated

from jakarta.xml.bind.annotation import XmlAttribute, XmlElement, XmlRootElement


@XmlRootElement(name="book")
class JaxbBook:
    isbn: Annotated[str | None, XmlAttribute]
    title: Annotated[str | None, XmlElement]
    subtitle: Annotated[str, XmlElement(defaultValue="Untitled", nillable=True)]
    authors: Annotated[list[str] | None, XmlElement(name="author")]

    def __init__(self):
        self.isbn = None
        self.title = None
        self.subtitle = None
        self.authors = None
