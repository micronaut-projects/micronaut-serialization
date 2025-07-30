package io.micronaut.serde.support.deserializers.buffer;

import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.Decoder;

import java.io.IOException;
import java.util.Collection;
import java.util.List;

@Internal
sealed class BufferedDecoderRoot extends AbstractBufferedDecoder implements BufferedDecoder permits BufferedDecoderLookaheadRoot {

    Decoder decoder;

    BufferedDecoderRoot(Decoder delegate, boolean consumeValues) {
        super(delegate, consumeValues);
    }

    @Override
    protected Collection<Decoder> nestedDecoders() {
        return decoder == null ? List.of() : List.of(decoder);
    }

    @Override
    protected <R extends Decoder> R nextDecoder(boolean consumeValues, DecoderProvider<R> provider, DecoderRemapper<R> remapper) throws IOException {
        if (decoder == null) {
            decoder = provider.provide();
        }
        if (decoder instanceof AbstractBufferedDecoder d) {
            d.reset(consumeValues);
        }
        return (R) decoder;
    }

    @Override
    protected void skipValue(boolean consumeValues) throws IOException {
//        throw new UnsupportedOperationException("BufferedDecoderRoot doesn't support skipValue");
    }

    @Override
    protected boolean decodeNull(boolean consumeValues) throws IOException {
        throw new UnsupportedOperationException("BufferedDecoderRoot doesn't support decodeNull");
    }

    @Override
    protected boolean consumeArray() {
        return false;
    }

    @Override
    protected boolean consumeObject() {
        return false;
    }

}
