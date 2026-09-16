from dataclasses import dataclass
from typing import Annotated

from com.fasterxml.jackson.annotation import JsonProperty
from micronaut.serde.annotation import Serdeable


@Serdeable  # <1>
@dataclass
class Book:
    title: str
    quantity: Annotated[int, JsonProperty("qty")]  # <2>
