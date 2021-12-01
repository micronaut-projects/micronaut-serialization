package example

import io.micronaut.core.type.Argument
import io.micronaut.serde.*
import jakarta.inject.Singleton

@Singleton // <1>
class PointSerde : Serde<Point> {
    // <2>
    override fun deserialize(
        decoder: Decoder,
        decoderContext: Deserializer.DecoderContext,
        type: Argument<in Point>
    ): Point {
        val array = decoder.decodeArray() // <3>
        val x = array.decodeInt()
        val y = array.decodeInt()
        array.finishStructure() // <4>
        return Point.valueOf(x, y) // <5>
    }

    override fun serialize(
        encoder: Encoder,
        context: Serializer.EncoderContext,
        value: Point,
        type: Argument<out Point>
    ) {
        val coords = value.coords()
        val array = encoder.encodeArray(type) // <7>
        array.encodeInt(coords[0])
        array.encodeInt(coords[1])
        array.finishStructure() // <8>
    }
}