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

    private boolean finished;
    private final List<Decoder> buffer = new ArrayList<>();
    @Nullable
    private ListIterator<Decoder> bufferIterator;

    BufferedArrayDecoder(Decoder delegate, boolean consumeValues) {
        super(delegate, consumeValues);
    }

    @Override
    protected Collection<Decoder> nestedDecoders() {
        return buffer;
    }

    protected Decoder lookupDecoder() {
        if (bufferIterator != null) {
            if (bufferIterator.hasNext()) {
                Decoder next = bufferIterator.next();
                bufferIterator.previous();
                return next;
            }
        }
        return delegate;
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
        R decoder = provider.provide();
        if (!consumeValues) {
            buffer.add(decoder);
        }
        return decoder;
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
        } else {
            if (!consumeValues) {
                buffer.add(delegate.decodeBuffer());
            } else {
                delegate.skipValue();
            }
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
        Decoder decoder = delegate.decodeBuffer();
        if (!consumeValues) {
            buffer.add(decoder);
        }
        return decoder.decodeNull();
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
        if (!delegate.hasNextArrayValue()) {
            finished = true;
            return false;
        }
        return true;
    }

    @Override
    protected void reset(boolean consumeValues) {
        super.reset(consumeValues);
        finished = false;
        bufferIterator = buffer.listIterator();
    }

    @Override
    public void finishStructure() throws IOException {
        super.finishStructure();
        bufferIterator = buffer.listIterator();
    }

    @Override
    public void finishStructure(boolean consumeLeftElements) throws IOException {
        super.finishStructure(consumeLeftElements);
        bufferIterator = buffer.listIterator();
    }

}
