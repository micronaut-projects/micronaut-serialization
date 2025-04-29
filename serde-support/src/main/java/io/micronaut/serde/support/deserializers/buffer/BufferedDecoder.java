package io.micronaut.serde.support.deserializers.buffer;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.LookaheadDecoder;

import java.io.IOException;

@Experimental
public interface BufferedDecoder extends Decoder {

    /**
     * Decode this object in a "non-consuming" fashion. Values read by the returned decoder can
     * still be read by other decoders, though possibly in a degraded state (e.g. decreased
     * numerical precision).
     *
     * @param type See {@link #decodeObject(Argument)}
     * @return The object decoder
     * @throws IOException If an unrecoverable error occurs
     */
    @NonNull
    BufferedDecoder decodeObjectNonConsuming(@NonNull Argument<?> type) throws IOException;

    @Override
    BufferedDecoder decodeObject() throws IOException;

    @Override
    BufferedDecoder decodeObject(Argument<?> type) throws IOException;

    @Override
    BufferedDecoder decodeArray() throws IOException;

    @Override
    BufferedDecoder decodeArray(Argument<?> type) throws IOException;

    /*
     * Create a new <i>buffered</i> decoder that can decode the same object multiple times. This
     * decoder is very restricted: It <i>must</i> be {@link AutoCloseable#close() closed} after
     * use. Each {@link #decodeObject()} or {@link #decodeArray()} call returns a decoder of the same object.
     *
     * <pre>{@code
     * try (Decoder bufferedDecoder = BufferedDecoder.prime(...)) {
     *     Decoder d1 = bufferedDecoder.decodeObject();
     *     decodeSomeProperties(d1);
     *     d1.finishStructure(true);
     *
     *     Decoder d2 = bufferedDecoder.decodeObject();
     *     decodeOtherProperties(d2);
     *     d2.finishStructure(true);
     * }
     * }</pre>
     *
     * @param decoder The input to read from. The buffered decoder will call {@link #decodeObject()} / {@link #decodeArray()}
     *                on this input exactly once
     * @return The primed decoder
     */
    static BufferedDecoder of(Decoder decoder) {
        if (decoder instanceof LookaheadDecoder lookaheadDecoder) {
            return of(lookaheadDecoder);
        }
        return new BufferedDecoderRoot(decoder, true);
    }

    static BufferedLookaheadDecoder of(LookaheadDecoder decoder) {
        return new BufferedDecoderLookaheadRoot(decoder, true);
    }

}
