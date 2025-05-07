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

@Internal
sealed class BufferedObjectDecoder extends AbstractBufferedDecoder<BufferedObjectDecoder.Entry> implements BufferedDecoder permits BufferedObjectLookaheadDecoder {

    private boolean finished;
    protected String currentKey;

    BufferedObjectDecoder(Decoder delegate, boolean consumeValues) {
        super(delegate, consumeValues);
    }

    @Override
    void reset(boolean consumeValues) {
        super.reset(consumeValues);
        finished = false;
        currentKey = null;
    }

    @Override
    protected Decoder getDecoder(Entry item) {
        return item.decoder;
    }

    @Override
    protected Entry createItem(Decoder decoder) {
        if (currentKey == null) {
            throw new IllegalStateException("No current key available");
        }
        return new Entry(currentKey, decoder);
    }

    @Override
    protected Entry updateItem(Entry item, Decoder decoder) {
        return new Entry(item.key, decoder);
    }

    @Override
    protected void valueConsumed() {
        currentKey = null;
    }

    @Override
    @Nullable
    public String decodeKey() throws IOException {
        if (currentKey != null) {
            throw new IllegalStateException("Value needs to be consumed before decoding next key");
        }
        Entry bufferEntry = findBufferEntry();
        if (bufferEntry != null) {
            return bufferEntry.key;
        }
        if (finished) {
            return null;
        }
        currentKey = delegate.decodeKey();
        if (currentKey == null) {
            finished = true;
            return null;
        }
        return currentKey;
    }

    @Override
    public boolean hasNextArrayValue() {
        throw new UnsupportedOperationException();
    }

    protected record Entry(String key, Decoder decoder) {
    }

}
