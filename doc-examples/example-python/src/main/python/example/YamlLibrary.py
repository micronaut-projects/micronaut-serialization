from dataclasses import dataclass

from micronaut.serde.annotation import Serdeable


@Serdeable
@dataclass
class YamlLibrary:
    name: str
    books: list[str]
