from dataclasses import dataclass
from typing import Annotated

from java.lang import Long

from micronaut.serde.annotation import Serdeable
from micronaut.serde.protobuf.annotation import ProtoField, ProtoType

# tag::clazz[]
@Serdeable
@dataclass(frozen=True)
class Telemetry:
    deviceId: str                                                                  # <1>
    recordedAt: Long
    temperatureMilliCelsius: Annotated[int, ProtoField(type=ProtoType.SINT32)]
# end::clazz[]
