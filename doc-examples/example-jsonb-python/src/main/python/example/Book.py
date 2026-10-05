from dataclasses import dataclass
from typing import Annotated

from jakarta.json.bind.annotation import JsonbProperty
from micronaut.serde.annotation import Serdeable


@Serdeable  # <1>
@dataclass
class Book:
    title: str
    quantity: Annotated[int, JsonbProperty("qty")]  # <2>
