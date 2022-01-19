package example;

import java.io.IOException;
import java.util.Objects;

import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serde;
import jakarta.inject.Singleton;

@Singleton
@Secondary // <1>
public class ReversePointSerde implements Serde<Point> {
    @Override
    public Point deserialize(
            Decoder decoder,
            DecoderContext context,
            Argument<? super Point> type) throws IOException {
        Decoder array = decoder.decodeArray();
        int y = array.decodeInt(); // <2>
        int x = array.decodeInt();
        array.finishStructure();
        return Point.valueOf(x, y);
    }

    @Override
    public void serialize(
            Encoder encoder,
            EncoderContext context,
            Argument<? extends Point> type, Point value) throws IOException {
        Objects.requireNonNull(value, "Point cannot be null");
        int[] coords = value.coords();
        Encoder array = encoder.encodeArray(type);
        array.encodeInt(coords[1]); // <3>
        array.encodeInt(coords[0]);
        array.finishStructure();
    }
}
