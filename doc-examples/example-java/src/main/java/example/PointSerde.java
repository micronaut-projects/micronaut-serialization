package example;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serde;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.Objects;

@Singleton // <1>
public class PointSerde implements Serde<Point> { // <2>
    @Override
    public Point deserialize(
            Decoder decoder,
            DecoderContext context,
            Argument<? super Point> type) throws IOException {
        try (Decoder array = decoder.decodeArray()) { // <3>
            int x = array.decodeInt();
            int y = array.decodeInt();
            return Point.valueOf(x, y); // <4>
        }
    }

    @Override
    public void serialize(
            Encoder encoder,
            EncoderContext context,
            Argument<? extends Point> type, Point value) throws IOException {
        Objects.requireNonNull(value, "Point cannot be null"); // <5>
        int[] coords = value.coords();
        try (Encoder array = encoder.encodeArray(type)) { // <6>
            array.encodeInt(coords[0]);
            array.encodeInt(coords[1]);
        }
    }
}
