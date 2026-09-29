from dataclasses import dataclass

from micronaut.serde.annotation import Serdeable


@Serdeable
@dataclass(frozen=True)
class Item:
    id: int
    name: str
    count: int
