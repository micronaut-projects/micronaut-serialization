from dataclasses import dataclass
from typing import Annotated

from micronaut.serde.annotation import Serdeable
from org.bson.codecs.pojo.annotations import BsonProperty


@Serdeable  # <1>
@dataclass
class Book:
    title: str
    quantity: Annotated[int, BsonProperty("qty")]  # <2>
