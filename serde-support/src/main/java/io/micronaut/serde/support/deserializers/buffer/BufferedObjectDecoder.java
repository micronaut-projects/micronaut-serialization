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
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.support.util.JsonNodeDecoder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

@Internal
sealed class BufferedObjectDecoder extends AbstractBufferedDecoder implements BufferedDecoder permits BufferedObjectLookaheadDecoder {

    private boolean finished;
    private final List<Map.Entry<String, Decoder>> buffer = new ArrayList<>();
    @Nullable
    protected ListIterator<Map.Entry<String, Decoder>> bufferIterator;
    @Nullable
    protected Map.Entry<String, Decoder> currentEntry;

    BufferedObjectDecoder(Decoder delegate, boolean consumeValues) {
        super(delegate, consumeValues);
    }

    @Override
    protected Collection<Decoder> nestedDecoders() {
        return buffer.stream().map(Map.Entry::getValue).toList();
    }

    @Override
    protected <R extends Decoder> R nextDecoder(boolean consumeValues, DecoderProvider<R> provider, DecoderRemapper<R> remapper) throws IOException {
        if (currentEntry == null) {
            throw new IllegalStateException("No current key available");
        }
        if (bufferIterator == null) {
            R decoder = provider.provide();
            if (consumeValues) {
                currentEntry = null;
                return decoder;
            }
            Map.Entry<String, Decoder> entry = Map.entry(currentEntry.getKey(), decoder);
            buffer.add(entry);
            currentEntry = null;
            return decoder;
        } else {
            Decoder decoder = currentEntry.getValue();
            R remapedDecoder = remapper.remap(decoder);
            if (consumeValues) {
                bufferIterator.remove();
            } else {
                bufferIterator.set(Map.entry(currentEntry.getKey(), remapedDecoder));
            }
            currentEntry = null;
            return remapedDecoder;
        }
    }

    @Override
    public JsonNode decodeNode() throws IOException {
        Decoder decoder = delegate();
        if (decoder instanceof JsonNodeDecoder jsonNodeDecoder) {
            JsonNode node = jsonNodeDecoder.getNode();
            return node;
        }
        return decoder.decodeNode();
    }

    @Override
    protected void skipValue(boolean consumeValues) throws IOException {
        if (currentEntry == null) {
            throw new IllegalStateException("No current key available");
        }
        if (bufferIterator == null) {
            if (consumeValues) {
                delegate.skipValue();
            } else {
                buffer.add(Map.entry(currentEntry.getKey(), delegate.decodeBuffer()));
            }
        } else {
            if (consumeValues) {
                bufferIterator.remove();
            }
        }
        currentEntry = null;
    }

    @Override
    protected boolean decodeNull(boolean consumeValues) throws IOException {
        if (currentEntry == null) {
            throw new IllegalStateException("No current key available");
        }
        Decoder decoder = currentEntry.getValue();
        boolean isNull = decoder.decodeNull();
        if (isNull) {
            if (consumeValues && bufferIterator == null) {
                buffer.add(currentEntry);
            }
            currentEntry = null;
        }
        return isNull;
    }

    @Override
    @Nullable
    public String decodeKey() throws IOException {
        if (currentEntry != null) {
            throw new IllegalStateException("Value needs to be consumed before decoding next key");
        }
        if (finished) {
            return null;
        }
        if (bufferIterator != null) {
            if (bufferIterator.hasNext()) {
                currentEntry = bufferIterator.next();
                return currentEntry.getKey();
            } else {
                bufferIterator = null; // End of buffered entries
            }
        }
        String key = delegate.decodeKey();
        if (key == null) {
            finished = true;
            return null;
        }
        currentEntry = Map.entry(key, delegate);
        return key;
    }


    public void finishStructure() throws IOException {
        super.finishStructure();
        finished = false;
        currentEntry = null;
        bufferIterator = buffer.listIterator();
    }

    @Override
    public void finishStructure(boolean consumeLeftElements) throws IOException {
        super.finishStructure(consumeLeftElements);
        finished = false;
        currentEntry = null;
        bufferIterator = buffer.listIterator();
    }

    @Override
    public boolean hasNextArrayValue() {
        throw new UnsupportedOperationException();
    }

}
