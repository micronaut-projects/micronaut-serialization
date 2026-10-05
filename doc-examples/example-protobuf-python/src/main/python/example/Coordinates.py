from dataclasses import dataclass
from typing import Annotated

from micronaut.serde.annotation import Serdeable
from micronaut.serde.protobuf.annotation import ProtoField, ProtoType

# tag::clazz[]
@Serdeable
@dataclass(frozen=True)
class Coordinates:
    latitudeMicros: Annotated[int, ProtoField(value=1, type=ProtoType.SFIXED32)]   # <1>
    longitudeMicros: Annotated[int, ProtoField(value=2, type=ProtoType.SFIXED32)]
# end::clazz[]
