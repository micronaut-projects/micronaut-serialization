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
import com.fasterxml.jackson.core.JsonToken;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.exceptions.InvalidFormatException;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.AbstractChildReuseStreamDecoder;
import io.micronaut.serde.support.AbstractStreamDecoder;

import java.io.EOFException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Implementation of the {@link io.micronaut.serde.Decoder} interface for Jackson.
 */
@Internal
public final class JacksonDecoder extends AbstractChildReuseStreamDecoder {
    // Changes must be reflected in {@link SpecializedJacksonDecoder}!

    static final AbstractStreamDecoder.TokenType[] REMAPPED = new TokenType[JsonToken.values().length];

    static {
        for (JsonToken value : JsonToken.values()) {
            REMAPPED[value.ordinal()] = remapToken(value);
        }
    }

    @Internal
    private final JsonParser parser;

    private JacksonDecoder(@NonNull JacksonDecoder parent) {
        super(parent);
        this.parser = parent.parser;
    }

    private JacksonDecoder(JsonParser parser, @NonNull Class<?> view) {
        super(view);
        this.parser = parser;
    }

    public static TokenType remapToken(JsonToken jsonToken) {
        return switch (jsonToken) {
            case START_OBJECT -> TokenType.START_OBJECT;
            case END_OBJECT -> TokenType.END_OBJECT;
            case START_ARRAY -> TokenType.START_ARRAY;
            case END_ARRAY -> TokenType.END_ARRAY;
            case FIELD_NAME -> TokenType.KEY;
            case VALUE_STRING -> TokenType.STRING;
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> TokenType.NUMBER;
            case VALUE_TRUE, VALUE_FALSE -> TokenType.BOOLEAN;
            case VALUE_NULL -> TokenType.NULL;
            default -> TokenType.OTHER;
        };
    }

    public static Decoder create(JsonParser parser) throws IOException {
        return create(parser, Object.class);
    }

    public static Decoder create(JsonParser parser, Class<?> view) throws IOException {
        if (!parser.hasCurrentToken()) {
            parser.nextToken();
            if (!parser.hasCurrentToken()) {
                throw new EOFException("No JSON input to parse");
            }
        }
        return new JacksonDecoder(parser, view);
    }

    @Override
    public IOException createDeserializationException(String message, Object invalidValue) {
        if (invalidValue != null) {
            return new InvalidFormatException(message + " \n at " + parser.getCurrentLocation(), null, invalidValue);
        } else {
            return new SerdeException(message + " \n at " + parser.getCurrentLocation());
        }
    }

    @Override
    protected TokenType currentToken() {
        JsonToken jsonToken = parser.currentToken();
        return JacksonDecoder.REMAPPED[jsonToken.ordinal()];
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
    protected String coerceScalarToString(TokenType currentToken) throws IOException {
        if (currentToken == TokenType.STRING) {
            return parser.getText();
        }
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
    protected int getInteger() throws IOException {
        return parser.getValueAsInt();
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

    @Override
    protected void consumeLeftElements(TokenType currentToken) throws IOException {
        while (currentToken != TokenType.END_ARRAY && currentToken != TokenType.END_OBJECT) {
            nextToken();
            currentToken = currentToken();
        }
    }
}
