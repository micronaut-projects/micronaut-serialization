package io.micronaut.serde.support.deserializers.buffer;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.LookaheadDecoder;

import java.io.IOException;

@Internal
final class BufferedDecoderLookaheadRoot extends BufferedDecoderRoot implements BufferedLookaheadDecoder {

    private final LookaheadDecoder lookaheadDecoder;

    BufferedDecoderLookaheadRoot(LookaheadDecoder delegate, boolean consumeValues) {
        super(delegate, consumeValues);
        this.lookaheadDecoder = delegate;
    }

    @Override
    public TokenType lookahead() throws IOException {
        return lookaheadDecoder.lookahead();
    }

    @Override
    protected BufferedArrayDecoder createArrayDecoder(Decoder delegate, boolean consumeValues) {
        return new BufferedArrayLookaheadDecoder((LookaheadDecoder) delegate, consumeValues);
    }

    @Override
    protected BufferedObjectDecoder createObjectDecoder(Decoder delegate, boolean consumeValues) {
        return new BufferedObjectLookaheadDecoder((LookaheadDecoder) delegate, consumeValues);
    }

    @Override
    public BufferedLookaheadDecoder decodeArray() throws IOException {
        return (BufferedLookaheadDecoder) super.decodeArray();
    }

    @Override
    public BufferedLookaheadDecoder decodeArray(Argument<?> type) throws IOException {
        return (BufferedLookaheadDecoder) super.decodeArray(type);
    }

    @Override
    public BufferedLookaheadDecoder decodeObject() throws IOException {
        return (BufferedLookaheadDecoder) super.decodeObject();
    }

    @Override
    public BufferedLookaheadDecoder decodeObject(Argument<?> type) throws IOException {
        return (BufferedLookaheadDecoder) super.decodeObject(type);
    }

    @Override
    public BufferedLookaheadDecoder decodeObjectNonConsuming(Argument<?> type) throws IOException {
        return (BufferedLookaheadDecoder) super.decodeObjectNonConsuming(type);
    }
}
