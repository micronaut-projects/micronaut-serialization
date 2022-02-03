package io.micronaut.serde.jackson;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.json.UTF8StreamJsonParser;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.serde.exceptions.InvalidFormatException;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.AbstractStreamDecoder;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Implementation of the {@link io.micronaut.serde.Decoder} interface for Jackson.
 *
 * Identical to {@link JacksonDecoder}, but specialized for {@link UTF8StreamJsonParser} for better inlining.
 */
@Internal
final class SpecializedJacksonDecoder extends AbstractStreamDecoder {
    private final UTF8StreamJsonParser parser;

    private SpecializedJacksonDecoder(@NonNull SpecializedJacksonDecoder parent) {
        super(parent);
        this.parser = parent.parser;
    }

    SpecializedJacksonDecoder(UTF8StreamJsonParser parser, @NonNull Class<?> view) {
        super(view);
        this.parser = parser;
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
    protected AbstractStreamDecoder.TokenType currentToken() {
        switch (parser.currentToken()) {
            case START_OBJECT:
                return AbstractStreamDecoder.TokenType.START_OBJECT;
            case END_OBJECT:
                return AbstractStreamDecoder.TokenType.END_OBJECT;
            case START_ARRAY:
                return AbstractStreamDecoder.TokenType.START_ARRAY;
            case END_ARRAY:
                return AbstractStreamDecoder.TokenType.END_ARRAY;
            case FIELD_NAME:
                return AbstractStreamDecoder.TokenType.KEY;
            case VALUE_STRING:
                return AbstractStreamDecoder.TokenType.STRING;
            case VALUE_NUMBER_INT:
            case VALUE_NUMBER_FLOAT:
                return AbstractStreamDecoder.TokenType.NUMBER;
            case VALUE_TRUE:
            case VALUE_FALSE:
                return AbstractStreamDecoder.TokenType.BOOLEAN;
            case VALUE_NULL:
                return AbstractStreamDecoder.TokenType.NULL;
            default:
                return AbstractStreamDecoder.TokenType.OTHER;
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
        return new SpecializedJacksonDecoder(this);
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
    protected int getInteger() throws IOException {
        return parser.getValueAsInt();
    }

    @Override
    protected double getDouble() throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NUMBER_FLOAT) {
            return parser.getDoubleValue();
        }
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
