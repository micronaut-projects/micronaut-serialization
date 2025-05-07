package io.micronaut.serde.support.deserializers.buffer;

import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.Decoder;

@Internal
sealed class BufferedDecoderRoot extends AbstractBufferedDecoder<Decoder> implements BufferedDecoder permits BufferedDecoderLookaheadRoot {

    Decoder decoder;

    BufferedDecoderRoot(Decoder delegate, boolean consumeValues) {
        super(delegate, consumeValues);
    }

    @Override
    protected boolean consumeArray() {
        return false;
    }

    @Override
    protected boolean consumeObject() {
        return false;
    }

    @Override
    protected Decoder findBufferEntry() {
        return decoder;
    }

    @Override
    protected void updateEntry(Decoder decoder) {
        this.decoder = decoder;
    }

    @Override
    protected Decoder getDecoder(Decoder decoder) {
        return decoder;
    }

    @Override
    protected Decoder createItem(Decoder decoder) {
        this.decoder = decoder;
        return decoder;
    }

    @Override
    protected void valueConsumed() {
    }

    @Override
    protected void internalFinishStructure() {
        // Don't close the first decoder
    }
}
