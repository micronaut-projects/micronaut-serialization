from jakarta.inject import Singleton
from micronaut.core.type import Argument
from micronaut.serde import Decoder, Encoder, Serde
from micronaut.serde.Deserializer import DecoderContext
from micronaut.serde.Serializer import EncoderContext

from example.Point import Point


@Singleton  # <1>
class PointSerde(Serde[Point]):  # <2>
    def deserialize(self, decoder: Decoder, context: DecoderContext, type: Argument) -> Point:
        array = decoder.decodeArray()  # <3>
        x = array.decodeInt()
        y = array.decodeInt()
        array.finishStructure()
        return Point.value_of(x, y)  # <4>

    def serialize(self, encoder: Encoder, context: EncoderContext, type: Argument, value: Point) -> None:
        if value is None:
            raise ValueError("Point cannot be null")  # <5>
        coords = value.coords()
        array = encoder.encodeArray(type)  # <6>
        array.encodeInt(coords[0])
        array.encodeInt(coords[1])
        array.finishStructure()
