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

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

import io.micronaut.serde.support.AbstractStreamDecoder;
import io.micronaut.serde.exceptions.SerdeException;
import jakarta.json.JsonNumber;
import jakarta.json.stream.JsonParser;

/**
 * Implementation of the {@link io.micronaut.serde.Decoder} interface for JSON-P.
 */
public class JsonParserDecoder extends AbstractStreamDecoder {
    private final JsonParser jsonParser;
    private JsonParser.Event currentEvent;

    public JsonParserDecoder(JsonParser jsonParser) {
        super(Object.class);
        this.jsonParser = jsonParser;
        this.currentEvent = jsonParser.next();
    }

    private JsonParserDecoder(JsonParserDecoder parent) {
        super(parent);
        this.jsonParser = parent.jsonParser;

        this.currentEvent = parent.currentEvent;
        parent.currentEvent = null;
    }

    @Override
    protected TokenType currentToken() {
        switch (currentEvent) {
            case START_ARRAY:
                return TokenType.START_ARRAY;
            case START_OBJECT:
                return TokenType.START_OBJECT;
            case KEY_NAME:
                return TokenType.KEY;
            case VALUE_STRING:
                return TokenType.STRING;
            case VALUE_NUMBER:
                return TokenType.NUMBER;
            case VALUE_TRUE:
            case VALUE_FALSE:
                return TokenType.BOOLEAN;
            case VALUE_NULL:
                return TokenType.NULL;
            case END_OBJECT:
                return TokenType.END_OBJECT;
            case END_ARRAY:
                return TokenType.END_ARRAY;
            default:
                return TokenType.OTHER;
        }
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
    protected String coerceScalarToString() {
        switch (currentEvent) {
            case VALUE_STRING:
            case VALUE_NUMBER:
                // only allowed for string and number
                return jsonParser.getString();
            case VALUE_TRUE:
                return "true";
            case VALUE_FALSE:
                return "false";
            default:
                throw new IllegalStateException("Method called in wrong context " + currentEvent);
        }
    }

    @Override
    protected AbstractStreamDecoder createChildDecoder() {
        return new JsonParserDecoder(this);
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
