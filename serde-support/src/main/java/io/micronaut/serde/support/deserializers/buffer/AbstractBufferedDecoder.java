package io.micronaut.serde.support.deserializers.buffer;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.DelegatingDecoder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Internal
abstract sealed class AbstractBufferedDecoder<E> extends DelegatingDecoder implements BufferedDecoder permits BufferedArrayDecoder, BufferedDecoderRoot, BufferedObjectDecoder {
    protected final Decoder delegate;
    private boolean consumeValues;

    private final List<E> buffer = new ArrayList<>();
    private int index = -1;

    private boolean lastConsumeLeftElements;
    private boolean finished;

    AbstractBufferedDecoder(Decoder delegate, boolean consumeValues) {
        this.delegate = delegate;
        this.consumeValues = consumeValues;
    }

    protected abstract Decoder getDecoder(E item);

    protected abstract E createItem(Decoder decoder);

    protected E updateItem(E item, Decoder decoder) {
        return item;
    }

    protected abstract void valueConsumed();

    protected BufferedArrayDecoder createArrayDecoder(Decoder delegate, boolean consumeValues) {
        return new BufferedArrayDecoder(delegate, consumeValues);
    }

    protected BufferedObjectDecoder createObjectDecoder(Decoder delegate, boolean consumeValues) {
        return new BufferedObjectDecoder(delegate, consumeValues);
    }

    @Nullable
    protected E findBufferEntry() {
        if (index == -1) {
            if (!buffer.isEmpty()) {
                // Start reading from the buffer
                index = 0;
                return buffer.get(index);
            }
        } else if (buffer.size() > index) {
            return buffer.get(index);
        }
        return null;
    }

    @Override
    public boolean hasNextArrayValue() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    @Nullable
    public String decodeKey() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    protected Decoder delegate() throws IOException {
        E bufferEntry = findBufferEntry();
        if (bufferEntry == null) {
            if (consumeValues) {
                // Trigger cleanup
                valueConsumed();
                return delegate;
            } else {
                return bufferCurrentValue();
            }
        }
        consumeValueIfNeeded();
        return getDecoder(bufferEntry);
    }

    private void consumeValueIfNeeded() {
        if (consumeValues) {
            buffer.remove(index);
            index = Math.min(index, buffer.size());
        }
        valueConsumed();
    }

    @Override
    public boolean decodeNull() throws IOException {
        E bufferEntry = findBufferEntry();
        if (bufferEntry == null) {
            if (consumeValues) {
                // Trigger cleanup
                if (delegate.decodeNull()) {
                    valueConsumed();
                    return true;
                }
                return false;
            } else {
                return bufferCurrentValue().decodeNull();
            }
        }
        consumeValueIfNeeded();
        return getDecoder(bufferEntry).decodeNull();
    }

    @Override
    public final void skipValue() throws IOException {
        E bufferEntry = findBufferEntry();
        if (bufferEntry == null) {
            bufferCurrentValue();
        } else {
            index++;
        }
    }

    private Decoder bufferCurrentValue() throws IOException {
        Decoder decoder = delegate.decodeBuffer();
        putToBuffer(decoder);
        return decoder;
    }

    private void putToBuffer(Decoder decoder) {
        if (index != -1 && buffer.size() != index) {
            throw new IllegalStateException("Illegal state");
        }
        buffer.add(createItem(decoder));
        valueConsumed();
        index = buffer.size();
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
        E bufferEntry = findBufferEntry();
        if (bufferEntry == null) {
            BufferedArrayDecoder decoder = createArrayDecoder(delegate.decodeArray(type), consumeValues);
            if (!consumeArray()) {
                putToBuffer(decoder);
            } else {
                valueConsumed();
            }
            return decoder;
        }
        Decoder bufferedDecoder = getDecoder(bufferEntry);
        BufferedArrayDecoder result;
        if (bufferedDecoder instanceof BufferedArrayDecoder bufferedArrayDecoder) {
            bufferedArrayDecoder.reset(consumeValues);
            result = bufferedArrayDecoder;
        } else {
            result = createArrayDecoder(bufferedDecoder, consumeValues);
            buffer.set(index, createItem(bufferedDecoder));
        }
        return result;
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
        E bufferEntry = findBufferEntry();
        if (bufferEntry == null) {
            BufferedObjectDecoder decoder = createObjectDecoder(delegate.decodeObject(type), consumeValues);
            if (!consumeObject()) {
                putToBuffer(decoder);
            } else {
                valueConsumed();
            }
            return decoder;
        }
        Decoder bufferedDecoder = getDecoder(bufferEntry);
        BufferedObjectDecoder result;
        if (bufferedDecoder instanceof BufferedObjectDecoder bufferedObjectDecoder) {
            bufferedObjectDecoder.reset(consumeValues);
            result = bufferedObjectDecoder;
        } else {
            result = createObjectDecoder(bufferedDecoder, consumeValues);
            buffer.set(index, updateItem(bufferEntry, bufferedDecoder));
        }
        return result;
    }

    @Override
    public void finishStructure() throws IOException {
        finishStructure(false);
    }

    @Override
    public void finishStructure(boolean consumeLeftElements) throws IOException {
        if (!consumeLeftElements && !buffer.isEmpty() && index != buffer.size()) {
            throw new IllegalStateException("Not all items consumed");
        }
        lastConsumeLeftElements = consumeLeftElements;
        finished = true;
    }

    void reset(boolean consumeValues) {
        if (!finished) {
            throw new IllegalStateException("Previous decoder didn't finish");
        }
        this.consumeValues = consumeValues;
        index = -1;
    }

    @Override
    public @NonNull IOException createDeserializationException(@NonNull String message, @Nullable Object invalidValue) {
        return delegate.createDeserializationException(message, invalidValue);
    }

    @Override
    public void close() throws IOException {
        for (E e : buffer) {
            if (e instanceof AbstractBufferedDecoder<?> bufferedDecoder) {
                bufferedDecoder.close();
            }
        }
        delegate.finishStructure(lastConsumeLeftElements);
    }

}
