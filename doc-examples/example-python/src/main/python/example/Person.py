from dataclasses import dataclass

from com.fasterxml.jackson.annotation import JsonFilter
from micronaut.serde.annotation import Serdeable


@Serdeable
@JsonFilter("person-filter")  # <1>
@dataclass
class Person:
    name: str
    preferredName: str | None = None
