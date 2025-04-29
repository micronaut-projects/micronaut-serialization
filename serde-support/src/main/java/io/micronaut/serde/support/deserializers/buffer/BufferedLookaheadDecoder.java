package io.micronaut.serde.support.deserializers.buffer;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.LookaheadDecoder;

import java.io.IOException;

@Experimental
public interface BufferedLookaheadDecoder extends BufferedDecoder, LookaheadDecoder {

    @NonNull
    BufferedLookaheadDecoder decodeObjectNonConsuming(@NonNull Argument<?> type) throws IOException;

    @Override
    BufferedLookaheadDecoder decodeObject() throws IOException;

    @Override
    BufferedLookaheadDecoder decodeObject(Argument<?> type) throws IOException;

    @Override
    BufferedLookaheadDecoder decodeArray() throws IOException;

    @Override
    BufferedLookaheadDecoder decodeArray(Argument<?> type) throws IOException;

}
