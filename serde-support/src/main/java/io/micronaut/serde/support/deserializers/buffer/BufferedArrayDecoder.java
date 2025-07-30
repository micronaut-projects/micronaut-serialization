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
package io.micronaut.serde.support.deserializers.buffer;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.Decoder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;

@Internal
sealed class BufferedArrayDecoder extends AbstractBufferedDecoder implements BufferedDecoder permits BufferedArrayLookaheadDecoder {

    private final List<Decoder> buffer = new ArrayList<>();
    @Nullable
    protected ListIterator<Decoder> bufferIterator;

    BufferedArrayDecoder(Decoder delegate, boolean consumeValues) {
        super(delegate, consumeValues);
    }

    @Override
    protected Collection<Decoder> nestedDecoders() {
        return buffer;
    }

    @Override
    protected <R extends Decoder> R nextDecoder(boolean consumeValues, DecoderProvider<R> provider, DecoderRemapper<R> remapper) throws IOException {
        if (bufferIterator != null) {
            if (bufferIterator.hasNext()) {
                Decoder decoder = bufferIterator.next();
                R remapperDecoder = remapper.remap(decoder);
                if (consumeValues) {
                    bufferIterator.remove();
                } else {
                    bufferIterator.set(remapperDecoder);
                }
                return remapperDecoder;
            }
            bufferIterator = null; // End of buffered entries
        }
        if (!delegateFinished) {
            R decoder = provider.provide();
            if (!consumeValues) {
                buffer.add(decoder);
            }
            return decoder;
        }
        throw new IllegalStateException("Decoder has already been finished");
    }

    @Override
    protected void skipValue(boolean consumeValues) throws IOException {
        if (bufferIterator != null) {
            if (bufferIterator.hasNext()) {
                bufferIterator.next();
                if (consumeValues) {
                    bufferIterator.remove();
                }
            } else {
                bufferIterator = null; // End of buffered entries
            }
        } else if (!delegateFinished) {
            if (!consumeValues) {
                buffer.add(delegate.decodeBuffer());
            } else {
                delegate.skipValue();
            }
        } else {
            throw new IllegalStateException("Decoder has already been finished");
        }
    }

    @Override
    protected boolean decodeNull(boolean consumeValues) throws IOException {
        if (bufferIterator != null) {
            if (bufferIterator.hasNext()) {
                Decoder decoder = bufferIterator.next();
                if (consumeValues) {
                    bufferIterator.remove();
                }
                return decoder.decodeNull();
            }
            bufferIterator = null; // End of buffered entries
        }
        if (!delegateFinished) {
            Decoder decoder = delegate.decodeBuffer();
            if (!consumeValues) {
                buffer.add(decoder);
            }
            return decoder.decodeNull();
        }
        throw new IllegalStateException("Decoder has already been finished");
    }

    @Override
    public boolean hasNextArrayValue() throws IOException {
        if (finished) {
            return false;
        }
        if (bufferIterator != null) {
            if (bufferIterator.hasNext()) {
                return true;
            }
            bufferIterator = null; // End of buffered entries
        }
        if (delegateFinished || !delegate.hasNextArrayValue()) {
            delegateFinished = true;
            return false;
        }
        return true;
    }

    @Override
    public void finishStructure(boolean consumeLeftElements) throws IOException {
        super.finishStructure(consumeLeftElements);
        if (!delegateFinished) {
            while (hasNextArrayValue()) {
                skipValue(false);
            }
            delegateFinished = true;
            delegate.finishStructure();
        }
    }

    @Override
    protected void reset(boolean consumeValues) {
        super.reset(consumeValues);
        finished = false;
        bufferIterator = buffer.listIterator();
    }

    @Override
    public String decodeKey() throws IOException {
        throw new UnsupportedOperationException();
    }
}
