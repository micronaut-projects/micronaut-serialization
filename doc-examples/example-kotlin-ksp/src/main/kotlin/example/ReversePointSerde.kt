package example

import io.micronaut.context.annotation.Secondary
import io.micronaut.core.type.Argument
import io.micronaut.serde.*
import jakarta.inject.Singleton
import java.util.*

@Singleton
@Secondary // <1>
class ReversePointSerde : Serde<Point> {
    override fun deserialize(
            decoder: Decoder,
            context: Deserializer.DecoderContext,
            type: Argument<in Point>
    ): Point {
        val array = decoder.decodeArray()
        val y = array.decodeInt() // <2>
        val x = array.decodeInt()
        array.finishStructure()
        return Point.valueOf(x, y)
    }

    override fun serialize(
            encoder: Encoder,
            context: Serializer.EncoderContext,
            type: Argument<out Point>,
            value: Point
    ) {
        Objects.requireNonNull(value, "Point cannot be null")
        val coords = value.coords()
        val array = encoder.encodeArray(type)
        array.encodeInt(coords[1]) // <3>
        array.encodeInt(coords[0])
        array.finishStructure()
    }
}