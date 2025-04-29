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
import io.micronaut.serde.Decoder;

import java.io.IOException;

@Internal
sealed class BufferedArrayDecoder extends AbstractBufferedDecoder<Decoder> implements BufferedDecoder permits BufferedArrayLookaheadDecoder {

    private boolean finished;
    private boolean nextArrayValuePresent;

    BufferedArrayDecoder(Decoder delegate, boolean consumeValues) {
        super(delegate, consumeValues);
    }

    @Override
    void reset(boolean consumeValues) {
        super.reset(consumeValues);
        finished = false;
        nextArrayValuePresent = false;
    }

    @Override
    public boolean hasNextArrayValue() throws IOException {
        if (finished) {
            return false;
        }
        Decoder bufferEntry = findBufferEntry();
        if (bufferEntry != null) {
            return true;
        }
        if (delegate.hasNextArrayValue()) {
            nextArrayValuePresent = true;
            return true;
        } else {
            finished = true;
            return false;
        }
    }

    @Override
    protected Decoder getDecoder(Decoder item) {
        return item;
    }

    @Override
    protected Decoder createItem(Decoder decoder) {
        return decoder;
    }

    @Override
    protected void valueConsumed() {
        if (nextArrayValuePresent) {
            nextArrayValuePresent = false;
        }
    }
}
