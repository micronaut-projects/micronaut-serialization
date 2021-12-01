package example

import io.micronaut.core.type.Argument
import io.micronaut.serde.*
import jakarta.inject.Singleton

@Singleton // <1>
class PointSerde : Serde<Point> { // <2>
    override fun deserialize(
        decoder: Decoder,
        decoderContext: Deserializer.DecoderContext,
        type: Argument<in Point>
    ): Point {
        decoder.decodeArray().use { // <3>
            val x = it.decodeInt()
            val y = it.decodeInt()
            return Point.valueOf(x, y) // <4>
        }
    }

    override fun serialize(
        encoder: Encoder,
        context: Serializer.EncoderContext,
        value: Point,
        type: Argument<out Point>
    ) {
        val coords = value.coords()
        encoder.encodeArray(type).use { // <6>
            it.encodeInt(coords[0])
            it.encodeInt(coords[1])
        }
    }
}