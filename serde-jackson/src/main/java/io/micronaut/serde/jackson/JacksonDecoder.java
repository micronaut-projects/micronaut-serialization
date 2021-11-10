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
package io.micronaut.serde.jackson;

import com.fasterxml.jackson.core.JsonParser;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.serde.AbstractStreamDecoder;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.exceptions.SerdeException;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

@Internal
public class JacksonDecoder extends AbstractStreamDecoder {
    @Internal
    public final JsonParser parser; // todo: hide

    private JacksonDecoder(@NonNull JacksonDecoder parent) {
        super(parent);
        this.parser = parent.parser;
    }

    private JacksonDecoder(JsonParser parser, @NonNull Class<?> view) {
        super(view);
        this.parser = parser;
    }

    public static Decoder create(JsonParser parser) throws IOException {
        return create(parser, Object.class);
    }

    public static Decoder create(JsonParser parser, Class<?> view) throws IOException {
        if (!parser.hasCurrentToken()) {
            parser.nextToken();
        }
        return new JacksonDecoder(parser, view);
    }

    @Override
    public IOException createDeserializationException(String message) {
        return new SerdeException(message + " \n at " + parser.getCurrentLocation());
    }

    @Override
    protected TokenType currentToken() {
        switch (parser.currentToken()) {
            case START_OBJECT:
                return TokenType.START_OBJECT;
            case END_OBJECT:
                return TokenType.END_OBJECT;
            case START_ARRAY:
                return TokenType.START_ARRAY;
            case END_ARRAY:
                return TokenType.END_ARRAY;
            case FIELD_NAME:
                return TokenType.KEY;
            case VALUE_STRING:
                return TokenType.STRING;
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                return TokenType.NUMBER;
            case VALUE_TRUE:
            case VALUE_FALSE:
                return TokenType.BOOLEAN;
            case VALUE_NULL:
                return TokenType.NULL;
            default:
                return TokenType.OTHER;
        }
    }

    @Override
    protected void nextToken() throws IOException {
        parser.nextToken();
    }

    @Override
    protected String getCurrentKey() throws IOException {
        return parser.getCurrentName();
    }

    @Override
    protected AbstractStreamDecoder createChildDecoder() {
        return new JacksonDecoder(this);
    }

    @Override
    protected String coerceScalarToString() throws IOException {
        return parser.getValueAsString();
    }

    @Override
    protected boolean getBoolean() throws IOException {
        return parser.getBooleanValue();
    }

    @Override
    protected long getLong() throws IOException {
        return parser.getValueAsLong();
    }

    @Override
    protected double getDouble() throws IOException {
        return parser.getValueAsDouble();
    }

    @Override
    protected BigInteger getBigInteger() throws IOException {
        return parser.getBigIntegerValue();
    }

    @Override
    protected BigDecimal getBigDecimal() throws IOException {
        return parser.getDecimalValue();
    }

    @Override
    protected Number getBestNumber() throws IOException {
        // behavior for floats depends on the settings on the parser, see getNumberValueExact
        return parser.getNumberValue();
    }

    @Override
    protected void skipChildren() throws IOException {
        parser.skipChildren();
    }
}
