from dataclasses import dataclass
from typing import Annotated

from java.lang import Long

from micronaut.serde.annotation import Serdeable
from micronaut.serde.protobuf.annotation import ProtoField, ProtoType

# tag::clazz[]
@Serdeable
@dataclass(frozen=True)
class LegacyReading:
    deviceId: Annotated[str, ProtoField(1)]
    recordedAt: Annotated[Long, ProtoField(2)]
    temperatureMilliCelsius: Annotated[int, ProtoField(value=3, type=ProtoType.SINT32)]
# end::clazz[]
