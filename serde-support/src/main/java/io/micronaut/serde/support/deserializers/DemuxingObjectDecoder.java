/*
 * Copyright 2017-2023 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.serde.support.deserializers;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.DelegatingDecoder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Special decoder that <i>demuxes</i> an object: The same object can be iterated over multiple
 * times with multiple decoders (concurrently or sequentially, though multi-threading is not
 * allowed). Properties that are skipped by one decoder can be read by another.
 * <br>
 * The use for this is subtype detection: An object is iterated over once to find the {@code type}
 * property to detect the subtype, and then the remaining properties (before and after the type)
 * are deserialized as normal.
 *
 * @author Jonas Konrad
 * @since 2.2.7
 */
@Internal
final class DemuxingObjectDecoder extends DelegatingDecoder {
    private final DemuxerState state;
    private final boolean consumeValues;
    private int nextKeyIndex;

    private DemuxingObjectDecoder(DemuxerState state, boolean consumeValues) {
        this.state = state;
        this.consumeValues = consumeValues;
        state.outputCount++;
    }

    /**
     * Create a new <i>primed</i> decoder that can decode the same object multiple times. This
     * decoder is very restricted: It <i>must</i> be {@link AutoCloseable#close() closed} after
     * use, and it <i>only</i> supports {@link #decodeObject()}. Each {@link #decodeObject()} call
     * returns a decoder of the same object.
     *
     * <pre>{@code
     * try (Decoder primed = DemuxingObjectDecoder.prime(...)) {
     *     Decoder d1 = primed.decodeObject();
     *     decodeSomeProperties(d1);
     *     d1.finishStructure(true);
     *
     *     Decoder d2 = primed.decodeObject();
     *     decodeOtherProperties(d2);
     *     d2.finishStructure(true);
     * }
     * }</pre>
     *
     * @param decoder The input to read from. The primed decoder will call {@link #decodeObject()}
     *                on this input exactly once
     * @param consumeValues If decoder should consume value or not
     * @return The primed decoder
     */
    public static Decoder prime(Decoder decoder, boolean consumeValues) {
        return new PrimedDecoder(decoder, consumeValues);
    }

    @Override
    public @Nullable String decodeKey() throws IOException {
        DemuxerState.Entry entry;
        do {
            entry = state.getEntry(nextKeyIndex++);
            if (entry == null) {
                // end of object
                return null;
            }
        } while (entry.consumed);
        return entry.key;
    }

    @NonNull
    private DemuxerState.Entry entryForValue() throws IOException {
        if (nextKeyIndex == 0) {
            throw new IllegalStateException("Must call decodeKey first");
        }
        DemuxerState.Entry entry = state.getEntry(nextKeyIndex - 1);
        if (entry == null) {
            throw new IllegalStateException("End of object, decodeKey should have returned null");
        }
        return entry;
    }

    @Override
    protected Decoder delegate() throws IOException {
        DemuxerState.Entry e = entryForValue();
        if (consumeValues) {
            return e.consume();
        } else {
            return e.buffer();
        }
    }

    @Override
    public boolean decodeNull() throws IOException {
        return entryForValue().decodeNull();
    }

    @Override
    public void skipValue() throws IOException {
        // normal checks, but don't consume the entry
        entryForValue();
    }

    @Override
    public void finishStructure() throws IOException {
        finishStructure(false);
    }

    @Override
    public void finishStructure(boolean consumeLeftElements) throws IOException {
        if (!consumeLeftElements &&
            // if nextKeyIndex > buffer.size, then decodeKey already returned null before
            nextKeyIndex <= state.buffer.size() &&
            // decodeKey will iterate over remaining entries, skipping those that are consumed by other streams
            decodeKey() != null) {

            throw new IllegalStateException("Not all items consumed");
        }
        state.removeOutput();
    }

    @Override
    public boolean hasNextArrayValue() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() throws IOException {
        finishStructure(true);
    }

    @Override
    public @NonNull IOException createDeserializationException(@NonNull String message, @Nullable Object invalidValue) {
        return state.delegate.createDeserializationException(message, invalidValue);
    }

    private static class DemuxerState {
        private final Decoder delegate;
        private final List<Entry> buffer = new ArrayList<>();
        private boolean hitEnd = false;
        private int outputCount = 0;

        DemuxerState(Decoder delegate) {
            this.delegate = delegate;
        }

        Entry getEntry(int i) throws IOException {
            if (buffer.size() > i) {
                return buffer.get(i);
            } else {
                if (buffer.size() != i) {
                    throw new IllegalArgumentException("Must access entries in sequence");
                }
                if (hitEnd) {
                    return null;
                }
                if (!buffer.isEmpty()) {
                    Entry lastEntry = buffer.get(buffer.size() - 1);
                    if (!lastEntry.consumed && lastEntry.buffer == null) {
                        lastEntry.buffer = delegate.decodeBuffer();
                    }
                }
                String key = delegate.decodeKey();
                if (key == null) {
                    hitEnd = true;
                    return null;
                }
                Entry entry = new Entry(key);
                buffer.add(entry);
                return entry;
            }
        }

        void removeOutput() throws IOException {
            if (--outputCount == 0) {
                delegate.finishStructure(true);
            }
        }

        private class Entry {
            final String key;
            Decoder buffer = null;
            boolean consumed = false;

            Entry(String key) {
                this.key = key;
            }

            Decoder consume() {
                if (consumed) {
                    throw new IllegalStateException("Entry already consumed");
                }
                consumed = true;
                if (buffer != null) {
                    return buffer;
                } else {
                    return delegate;
                }
            }

            Decoder buffer() throws IOException {
                if (buffer != null) {
                    return buffer;
                } else {
                    buffer = delegate.decodeBuffer();
                }
                return buffer;
            }

            boolean decodeNull() throws IOException {
                // this call has the expectation that a proper consume() will follow
                if (consumed) {
                    throw new IllegalStateException("Entry already consumed");
                }
                if (buffer != null) {
                    return buffer.decodeNull();
                } else {
                    return delegate.decodeNull();
                }
            }
        }
    }

    private static class PrimedDecoder extends DelegatingDecoder {
        private final Decoder delegate;
        private final boolean absorbValues;
        @Nullable
        private DemuxerState state;

        PrimedDecoder(Decoder delegate, boolean absorbValues) {
            this.delegate = delegate;
            this.absorbValues = absorbValues;
        }

        @Override
        public @NonNull DemuxingObjectDecoder decodeObject() throws IOException {
            if (state == null) {
                state = new DemuxerState(delegate.decodeObject());
                state.outputCount++;
            }
            return new DemuxingObjectDecoder(state, absorbValues);
        }

        @Override
        public @NonNull DemuxingObjectDecoder decodeObject(@NonNull Argument<?> type) throws IOException {
            if (state == null) {
                state = new DemuxerState(delegate.decodeObject(type));
                state.outputCount++;
            }
            return new DemuxingObjectDecoder(state, absorbValues);
        }

        @Override
        public boolean decodeNull() throws IOException {
            return false;
        }

        @Override
        protected Decoder delegate() throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        public @NonNull IOException createDeserializationException(@NonNull String message, @Nullable Object invalidValue) {
            return delegate.createDeserializationException(message, invalidValue);
        }

        @Override
        public void close() throws IOException {
            if (state == null) {
                state = new DemuxerState(delegate.decodeObject());
                state.outputCount++;
            }
            state.removeOutput();
        }
    }
}
