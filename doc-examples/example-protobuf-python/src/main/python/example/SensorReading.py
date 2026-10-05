from dataclasses import dataclass
from typing import Annotated

from java.lang import Long

from micronaut.serde.annotation import Serdeable
from micronaut.serde.protobuf.annotation import ProtoField, ProtoType

from example.Coordinates import Coordinates

# tag::clazz[]
@Serdeable
@dataclass(frozen=True)
class SensorReading:
    deviceId: Annotated[str, ProtoField(1)]
    recordedAt: Annotated[Long, ProtoField(2)]
    temperatureMilliCelsius: Annotated[int, ProtoField(value=3, type=ProtoType.SINT32)]   # <1>
    signalStrengths: Annotated[list[int] | None, ProtoField(4)]                            # <2>
    location: Annotated[Coordinates | None, ProtoField(5)]                                 # <3>
    batteryLow: Annotated[bool, ProtoField(6)]
# end::clazz[]
