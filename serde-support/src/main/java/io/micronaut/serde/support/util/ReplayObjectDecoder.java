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
package io.micronaut.serde.support.util;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Decoder;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Iterator;
import java.util.Map;

/**
 * Implementation of the {@link Decoder} that replays lookahead tokens and then delegates to the original decoder.
 *
 * @author Denis Stepanov
 * @since 2.3
 */
@Internal
final class ReplayObjectDecoder extends JsonNodeDecoder {

    private final Iterator<Map.Entry<String, JsonNode>> fieldsIterator;
    private final boolean finished;
    private Map.Entry<String, JsonNode> currentLookahead;
    private final Decoder decoder;
    private boolean decodeObjectCalled;

    ReplayObjectDecoder(RemainingLimits remainingLimits,
                        Iterator<Map.Entry<String, JsonNode>> fieldsIterator,
                        boolean finished,
                        Decoder decoder) {
        super(remainingLimits);
        this.fieldsIterator = fieldsIterator;
        this.finished = finished;
        this.decoder = decoder;
    }

    @Override
    public boolean hasNextArrayValue() {
        return false;
    }

    @Override
    public String decodeKey() throws IOException {
        if (currentLookahead != null) {
            throw new IllegalStateException("The value not read");
        }
        if (fieldsIterator.hasNext()) {
            currentLookahead = fieldsIterator.next();
            return currentLookahead.getKey();
        }
        if (finished) {
            return null;
        }
        return decoder.decodeKey();
    }

    @Override
    public Decoder decodeArray(Argument<?> type) throws IOException {
        if (currentLookahead != null) {
            return super.decodeArray(type);
        }
        return decoder.decodeArray();
    }

    @Override
    public Decoder decodeObject(Argument<?> type) throws IOException {
        if (!decodeObjectCalled) {
            decodeObjectCalled = true;
            return this;
        }
        if (currentLookahead != null) {
            return super.decodeObject(type);
        }
        return decoder.decodeObject();
    }

    @Override
    public String decodeString() throws IOException {
        if (currentLookahead != null) {
            return super.decodeString();
        }
        return decoder.decodeString();
    }

    @Override
    public boolean decodeBoolean() throws IOException {
        if (currentLookahead != null) {
            return super.decodeBoolean();
        }
        return decoder.decodeBoolean();
    }

    @Override
    public byte decodeByte() throws IOException {
        if (currentLookahead != null) {
            return super.decodeByte();
        }
        return decoder.decodeByte();
    }

    @Override
    public short decodeShort() throws IOException {
        if (currentLookahead != null) {
            return super.decodeShort();
        }
        return decoder.decodeShort();
    }

    @Override
    public char decodeChar() throws IOException {
        if (currentLookahead != null) {
            return super.decodeChar();
        }
        return decoder.decodeChar();
    }

    @Override
    public int decodeInt() throws IOException {
        if (currentLookahead != null) {
            return super.decodeInt();
        }
        return decoder.decodeInt();
    }

    @Override
    public long decodeLong() throws IOException {
        if (currentLookahead != null) {
            return super.decodeLong();
        }
        return decoder.decodeLong();
    }

    @Override
    public float decodeFloat() throws IOException {
        if (currentLookahead != null) {
            return super.decodeFloat();
        }
        return decoder.decodeFloat();
    }

    @Override
    public double decodeDouble() throws IOException {
        if (currentLookahead != null) {
            return super.decodeDouble();
        }
        return decoder.decodeDouble();
    }

    @Override
    public BigInteger decodeBigInteger() throws IOException {
        if (currentLookahead != null) {
            return super.decodeBigInteger();
        }
        return decoder.decodeBigInteger();
    }

    @Override
    public BigDecimal decodeBigDecimal() throws IOException {
        if (currentLookahead != null) {
            return super.decodeBigDecimal();
        }
        return decoder.decodeBigDecimal();
    }

    @Override
    public byte @NonNull [] decodeBinary() throws IOException {
        if (currentLookahead != null) {
            return super.decodeBinary();
        }
        return decoder.decodeBinary();
    }

    @Override
    public boolean decodeNull() throws IOException {
        if (currentLookahead != null) {
            return super.decodeNull();
        }
        return decoder.decodeNull();
    }

    @Override
    public Object decodeArbitrary() throws IOException {
        if (currentLookahead != null) {
            return super.decodeArbitrary();
        }
        return decoder.decodeArbitrary();
    }

    @NonNull
    @Override
    public JsonNode decodeNode() throws IOException {
        if (currentLookahead != null) {
            return super.decodeNode();
        }
        return decoder.decodeNode();
    }

    @Override
    public Decoder decodeBuffer() throws IOException {
        if (currentLookahead != null) {
            return super.decodeBuffer();
        }
        return decoder.decodeBuffer();
    }

    @Override
    public void skipValue() throws IOException {
        if (currentLookahead != null) {
            currentLookahead = null;
        } else {
            decoder.skipValue();
        }
    }

    @Override
    public void finishStructure(boolean consumeLeftElements) throws IOException {
        String key = decodeKey();
        while (key != null) {
            skipValue();
            key = decodeKey();
        }
        decoder.finishStructure();
    }

    @Override
    protected JsonNode peekValue() throws IOException {
        if (currentLookahead == null) {
            if (!finished) {
                return decoder.decodeNode();
            }
            throw new IllegalStateException("Current key not read or null");
        }
        return currentLookahead.getValue();
    }

}
