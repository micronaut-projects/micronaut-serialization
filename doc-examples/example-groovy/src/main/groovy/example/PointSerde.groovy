package example

import io.micronaut.core.type.Argument
import io.micronaut.serde.Decoder
import io.micronaut.serde.Encoder
import io.micronaut.serde.Serde
import jakarta.inject.Singleton

@Singleton // <1>
class PointSerde implements Serde<Point> { // <2>
    @Override
    Point deserialize(
            Decoder decoder,
            DecoderContext decoderContext,
            Argument<? super Point> type) throws IOException {
        Decoder array = decoder.decodeArray() // <3>
        int x = array.decodeInt()
        int y = array.decodeInt()
        array.finishStructure() // <4>
        return Point.valueOf(x, y) // <5>
    }

    @Override
    void serialize(
            Encoder encoder,
            EncoderContext context,
            Point value,
            Argument<? extends Point> type) throws IOException {
        Objects.requireNonNull(value, "Point cannot be null") // <6>
        int[] coords = value.coords()
        Encoder array = encoder.encodeArray(type) // <7>
        array.encodeInt(coords[0])
        array.encodeInt(coords[1])
        array.finishStructure() // <8>
    }
}
