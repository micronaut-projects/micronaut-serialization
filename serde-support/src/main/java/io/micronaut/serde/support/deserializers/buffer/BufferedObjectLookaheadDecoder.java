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
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.LookaheadDecoder;

import java.io.IOException;

@Internal
final class BufferedObjectLookaheadDecoder extends BufferedObjectDecoder implements BufferedLookaheadDecoder {

    private final LookaheadDecoder lookaheadDecoder;

    BufferedObjectLookaheadDecoder(LookaheadDecoder delegate, boolean consumeValues) {
        super(delegate, consumeValues);
        this.lookaheadDecoder = delegate;
    }

    @Override
    public TokenType lookahead() throws IOException {
        TokenType lookahead = lookaheadDecoder.lookahead();
//        if (lookahead == TokenType.START_OBJECT || index == -1) {
//            return TokenType.START_OBJECT;
//        }

        Entry bufferEntry = findBufferEntry();
        if (bufferEntry != null) {
            if (currentKey == null) {
                return TokenType.KEY;
            }
            return ((LookaheadDecoder) bufferEntry.decoder()).lookahead();
        }
        return lookahead;
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
