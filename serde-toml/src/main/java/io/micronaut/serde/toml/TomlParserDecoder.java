/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.serde.toml;

import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.exceptions.InvalidFormatException;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.AbstractStreamDecoder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;

import java.io.EOFException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * TOML decoder backed by Jackson's TOML parser.
 */
@Internal
final class TomlParserDecoder extends AbstractStreamDecoder {

    private final JsonParser parser;
    @Nullable
    private JsonToken currentToken;

    TomlParserDecoder(@NonNull JsonParser parser, @NonNull RemainingLimits remainingLimits) throws IOException {
        super(remainingLimits);
        this.parser = parser;
        if (parser.hasCurrentToken()) {
            currentToken = parser.currentToken();
        } else {
            currentToken = parser.nextToken();
        }
        if (currentToken == null) {
            throw new EOFException("No TOML input to parse");
        }
    }

    @Override
    public void finishStructure(boolean consumeLeftElements) throws IOException {
        super.finishStructure(consumeLeftElements);
        nextToken();
    }

    @Override
    protected @Nullable TokenType currentToken() {
        if (currentToken == null) {
            return null;
        }
        return switch (currentToken) {
            case START_ARRAY -> TokenType.START_ARRAY;
            case END_ARRAY -> TokenType.END_ARRAY;
            case START_OBJECT -> TokenType.START_OBJECT;
            case END_OBJECT -> TokenType.END_OBJECT;
            case PROPERTY_NAME -> TokenType.KEY;
            case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> TokenType.NUMBER;
            case VALUE_STRING -> TokenType.STRING;
            case VALUE_TRUE, VALUE_FALSE -> TokenType.BOOLEAN;
            case VALUE_NULL -> TokenType.NULL;
            default -> TokenType.OTHER;
        };
    }

    @Override
    protected void nextToken() throws IOException {
        currentToken = parser.nextToken();
    }

    @Override
    protected String getCurrentKey() throws IOException {
        return parser.currentName();
    }

    @Override
    protected String coerceScalarToString(TokenType currentToken) throws IOException {
        return parser.getValueAsString();
    }

    @Override
    protected String getString() throws IOException {
        return parser.getText();
    }

    @Override
    protected boolean getBoolean() throws IOException {
        return parser.getBooleanValue();
    }

    @Override
    protected long getLong() throws IOException {
        return parser.getLongValue();
    }

    @Override
    protected double getDouble() throws IOException {
        return parser.getDoubleValue();
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
        return parser.getNumberValueExact();
    }

    @Override
    protected void skipChildren() throws IOException {
        if (currentToken == JsonToken.START_ARRAY || currentToken == JsonToken.START_OBJECT) {
            parser.skipChildren();
            currentToken = parser.currentToken();
        }
    }

    @Override
    public IOException createDeserializationException(String message, Object invalidValue) {
        if (invalidValue != null) {
            return new InvalidFormatException(message + " \n at " + parser.currentLocation(), null, invalidValue);
        }
        return new SerdeException(message + " \n at " + parser.currentLocation());
    }
}
