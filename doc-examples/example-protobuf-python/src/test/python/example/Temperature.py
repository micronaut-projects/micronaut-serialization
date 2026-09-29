from dataclasses import dataclass
from typing import Annotated

from micronaut.serde.annotation import Serdeable
from micronaut.serde.protobuf.annotation import ProtoField, ProtoType

# The same value under the two representations protobuf offers for a signed 32-bit integer.

# tag::clazz[]
@Serdeable
@dataclass(frozen=True)
class AsInt32:
    milliCelsius: Annotated[int, ProtoField(1)]


@Serdeable
@dataclass(frozen=True)
class AsSint32:
    milliCelsius: Annotated[int, ProtoField(value=1, type=ProtoType.SINT32)]
# end::clazz[]
