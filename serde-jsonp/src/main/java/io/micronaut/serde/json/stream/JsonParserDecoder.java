/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.serde.json.stream;

import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.AbstractStreamDecoder;
import jakarta.json.JsonNumber;
import jakarta.json.stream.JsonParser;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Implementation of the {@link io.micronaut.serde.Decoder} interface for JSON-P.
 */
public class JsonParserDecoder extends AbstractStreamDecoder {
    private final JsonParser jsonParser;
    private JsonParser.Event currentEvent;

    public JsonParserDecoder(JsonParser jsonParser) {
        this(jsonParser, DEFAULT_LIMITS);
    }

    @Internal
    JsonParserDecoder(JsonParser jsonParser, RemainingLimits remainingLimits) {
        super(remainingLimits);
        this.jsonParser = jsonParser;
        this.currentEvent = jsonParser.next();
    }

    @Override
    public void finishStructure(boolean consumeLeftElements) throws IOException {
        super.finishStructure(consumeLeftElements);
        nextToken();
    }

    @Override
    protected TokenType currentToken() {
        return switch (currentEvent) {
            case START_ARRAY -> TokenType.START_ARRAY;
            case START_OBJECT -> TokenType.START_OBJECT;
            case KEY_NAME -> TokenType.KEY;
            case VALUE_STRING -> TokenType.STRING;
            case VALUE_NUMBER -> TokenType.NUMBER;
            case VALUE_TRUE, VALUE_FALSE -> TokenType.BOOLEAN;
            case VALUE_NULL -> TokenType.NULL;
            case END_OBJECT -> TokenType.END_OBJECT;
            case END_ARRAY -> TokenType.END_ARRAY;
            default -> TokenType.OTHER;
        };
    }

    @Override
    protected void nextToken() {
        if (jsonParser.hasNext()) {
            currentEvent = jsonParser.next();
        } else {
            // EOF
            currentEvent = null;
        }
    }

    @Override
    protected String getCurrentKey() {
        return jsonParser.getString();
    }

    @Override
    protected String coerceScalarToString(TokenType currentToken) {
        return switch (currentEvent) {
            case VALUE_STRING, VALUE_NUMBER ->
                // only allowed for string and number
                jsonParser.getString();
            case VALUE_TRUE -> "true";
            case VALUE_FALSE -> "false";
            default ->
                throw new IllegalStateException("Method called in wrong context " + currentEvent);
        };
    }

    @Override
    protected String getString() {
        return jsonParser.getString();
    }

    @Override
    protected boolean getBoolean() {
        return currentEvent == JsonParser.Event.VALUE_TRUE;
    }

    @Override
    protected long getLong() {
        return jsonParser.getLong();
    }

    @Override
    protected double getDouble() {
        return jsonParser.getBigDecimal().doubleValue();
    }

    @Override
    protected BigInteger getBigInteger() {
        return jsonParser.getBigDecimal().toBigInteger();
    }

    @Override
    protected BigDecimal getBigDecimal() {
        return jsonParser.getBigDecimal();
    }

    @Override
    protected Number getBestNumber() {
        return ((JsonNumber) jsonParser.getValue()).numberValue();
    }

    @Override
    protected void skipChildren() {
        if (currentEvent == JsonParser.Event.START_OBJECT) {
            jsonParser.skipObject();
        } else if (currentEvent == JsonParser.Event.START_ARRAY) {
            jsonParser.skipArray();
        }
    }

    @Override
    public IOException createDeserializationException(String message, Object invalidValue) {
        return new SerdeException(message + " \n at " + jsonParser.getLocation());
    }
}
