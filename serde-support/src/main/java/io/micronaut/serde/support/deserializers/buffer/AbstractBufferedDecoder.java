package io.micronaut.serde.support.deserializers.buffer;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.DelegatingDecoder;
import io.micronaut.serde.support.util.JsonNodeDecoder;

import java.io.IOException;
import java.util.Collection;

@Internal
abstract sealed class AbstractBufferedDecoder extends DelegatingDecoder implements BufferedDecoder permits BufferedArrayDecoder, BufferedDecoderRoot, BufferedObjectDecoder {
    protected final Decoder delegate;
    private boolean consumeValues;

    protected int index = -1;

    private boolean lastConsumeLeftElements;
    private boolean finished = true;

    AbstractBufferedDecoder(Decoder delegate, boolean consumeValues) {
        this.delegate = delegate;
        this.consumeValues = consumeValues;
    }

    protected abstract <R extends Decoder> R nextDecoder(boolean consumeValues,
                                                         DecoderProvider<R> provider,
                                                         DecoderRemapper<R> remapper) throws IOException;

    protected abstract void skipValue(boolean consumeValues) throws IOException ;

    protected final Decoder nextDecoder(boolean consumeValues) throws IOException {
        return nextDecoder(consumeValues, delegate::decodeBuffer, decoder -> decoder);
    }

    protected abstract boolean decodeNull(boolean consumeValues) throws IOException;

    boolean isFinished() {
        return finished;
    }

    protected BufferedArrayDecoder createArrayDecoder(Decoder delegate, boolean consumeValues) {
        return new BufferedArrayDecoder(delegate, consumeValues);
    }

    protected BufferedObjectDecoder createObjectDecoder(Decoder delegate, boolean consumeValues) {
        return new BufferedObjectDecoder(delegate, consumeValues);
    }

    @Override
    public boolean hasNextArrayValue() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public JsonNode decodeNode() throws IOException {
        Decoder decoder = delegate();
        if (decoder instanceof JsonNodeDecoder jsonNodeDecoder) {
            return jsonNodeDecoder.getNode();
        }
        return decoder.decodeNode();
    }

    @Override
    @Nullable
    public String decodeKey() throws IOException {
        throw new UnsupportedOperationException();
    }

    protected abstract Collection<Decoder> nestedDecoders();

    @Override
    protected Decoder delegate() throws IOException {
        return nextDecoder(consumeValues);
    }

    @Override
    public boolean decodeNull() throws IOException {
        return decodeNull(consumeValues);
    }

    @Override
    public void skipValue() throws IOException {
        skipValue(false);
    }

    @Override
    public BufferedDecoder decodeObject() throws IOException {
        return decodeObject(Argument.OBJECT_ARGUMENT);
    }

    @Override
    public BufferedDecoder decodeArray() throws IOException {
        return decodeArray(Argument.OBJECT_ARGUMENT);
    }

    @Override
    public BufferedDecoder decodeArray(Argument<?> type) throws IOException {
        return nextDecoder(
            consumeArray(),
            () -> createArrayDecoder(delegate.decodeArray(type), consumeValues),
            decoder -> {
                if (decoder instanceof BufferedArrayDecoder arrayDecoder) {
                    arrayDecoder.reset(consumeValues);
                    return arrayDecoder;
                }
                return createArrayDecoder(decoder, consumeValues);
            }
        );
    }

    protected boolean consumeArray() {
        return consumeValues;
    }

    protected boolean consumeObject() {
        return consumeValues;
    }

    @Override
    @NonNull
    public BufferedDecoder decodeObject(@NonNull Argument<?> type) throws IOException {
        return decodeObject(type, consumeValues);
    }

    @NonNull
    public BufferedDecoder decodeObjectNonConsuming(@NonNull Argument<?> type) throws IOException {
        return decodeObject(type, false);
    }

    private BufferedObjectDecoder decodeObject(Argument<?> type, boolean consumeValues) throws IOException {
        return nextDecoder(
            consumeObject(),
            () -> createObjectDecoder(delegate.decodeObject(type), consumeValues),
            decoder -> {
                if (decoder instanceof BufferedObjectDecoder d) {
                    d.reset(consumeValues);
                    return d;
                }
                return createObjectDecoder(decoder, consumeValues);
            }
        );
    }

    @Override
    public void finishStructure() throws IOException {
        finishStructure(false);
    }

    @Override
    public void finishStructure(boolean consumeLeftElements) throws IOException {
//        if (!consumeLeftElements && !buffer.isEmpty() && index != buffer.size()) {
//            throw new IllegalStateException("Not all items consumed");
//        }
        lastConsumeLeftElements = consumeLeftElements;
        finished = true;
        for (Decoder decoder : nestedDecoders()) {
            if (decoder instanceof AbstractBufferedDecoder bufferedDecoder) {
                bufferedDecoder.finishStructure(consumeLeftElements);
            }
        }
    }

    protected void reset(boolean consumeValues) {
        if (!finished) {
            throw new IllegalStateException("Previous decoder didn't finish");
        }
        for (Decoder decoder : nestedDecoders()) {
            if (decoder instanceof AbstractBufferedDecoder bufferedDecoder) {
                bufferedDecoder.reset(consumeValues);
            }
        }
    }

    @Override
    public @NonNull IOException createDeserializationException(@NonNull String message, @Nullable Object invalidValue) {
        return delegate.createDeserializationException(message, invalidValue);
    }

    @Override
    public void close() throws IOException {
        for (Decoder decoder : nestedDecoders()) {
            if (decoder instanceof AbstractBufferedDecoder bufferedDecoder) {
                bufferedDecoder.close();
            }
        }
        reset(consumeValues);
        internalFinishStructure();
    }

    protected void internalFinishStructure() throws IOException {
        delegate.finishStructure(lastConsumeLeftElements);
    }

    protected interface DecoderProvider<R extends Decoder> {

        R provide() throws IOException;

    }

    protected interface DecoderRemapper<R extends Decoder> {

        R remap(Decoder decoder) throws IOException;

    }

}
