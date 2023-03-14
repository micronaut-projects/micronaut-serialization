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
package io.micronaut.serde.oracle.jdbc.json;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.serde.exceptions.InvalidFormatException;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.AbstractStreamDecoder;
import oracle.sql.json.OracleJsonArray;
import oracle.sql.json.OracleJsonParser;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;

/**
 * Implementation of the {@link io.micronaut.serde.Decoder} interface for Oracle JDBC JSON.
 *
 * @author Denis Stepanov
 * @since 1.2.0
 */
@Internal
public final class OracleJdbcJsonParserDecoder extends AbstractStreamDecoder {

    private static final String METHOD_CALLED_IN_WRONG_CONTEXT = "Method called in wrong context ";

    private final OracleJsonParser jsonParser;
    private OracleJsonParser.Event currentEvent;

    OracleJdbcJsonParserDecoder(OracleJsonParser jsonParser) {
        super(Object.class);
        this.jsonParser = jsonParser;
        this.currentEvent = jsonParser.next();
    }

    OracleJdbcJsonParserDecoder(OracleJdbcJsonParserDecoder parent) {
        super(parent);
        this.jsonParser = parent.jsonParser;

        this.currentEvent = parent.currentEvent;
        parent.currentEvent = null;
    }

    @Override
    protected TokenType currentToken() {
        return switch (currentEvent) {
            case START_ARRAY -> TokenType.START_ARRAY;
            case START_OBJECT -> TokenType.START_OBJECT;
            case KEY_NAME -> TokenType.KEY;
            case VALUE_STRING -> TokenType.STRING;
            case VALUE_DECIMAL, VALUE_DOUBLE, VALUE_FLOAT -> TokenType.NUMBER;
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
    protected String coerceScalarToString() {
        return switch (currentEvent) {
            case VALUE_STRING, VALUE_DECIMAL, VALUE_DOUBLE, VALUE_FLOAT, VALUE_INTERVALDS, VALUE_INTERVALYM ->
                // only allowed for string, number
                // additionally for processing string values from VALUE_INTERVALDS, VALUE_INTERVALYM
                // in combination with custom de/serializers configured for Oracle Json parsing
                // which is needed to transform from VALUE_INTERVALDS to java.time.Duration
                jsonParser.getString();
            case VALUE_BINARY ->
                // VALUE_BINARY will return Base16 encoded string and when serializing just write back the same string value
                // which should work fine
                jsonParser.getValue().asJsonBinary().getString();
            case VALUE_TRUE -> StringUtils.TRUE;
            case VALUE_FALSE -> StringUtils.FALSE;
            default ->
                throw new IllegalStateException(METHOD_CALLED_IN_WRONG_CONTEXT + currentEvent);
        };
    }

    @Override
    protected AbstractStreamDecoder createChildDecoder() {
        return new OracleJdbcJsonParserDecoder(this);
    }

    @Override
    protected boolean getBoolean() {
        return currentEvent == OracleJsonParser.Event.VALUE_TRUE;
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
        return switch (currentEvent) {
            case VALUE_DECIMAL -> jsonParser.getLong();
            case VALUE_DOUBLE -> jsonParser.getDouble();
            case VALUE_FLOAT -> jsonParser.getFloat();
            default ->
                throw new IllegalStateException(METHOD_CALLED_IN_WRONG_CONTEXT + currentEvent);
        };
    }

    @Override
    protected void skipChildren() {
        if (currentEvent == OracleJsonParser.Event.START_OBJECT) {
            jsonParser.skipObject();
        } else if (currentEvent == OracleJsonParser.Event.START_ARRAY) {
            jsonParser.skipArray();
        }
    }

    @Override
    @NonNull
    public IOException createDeserializationException(@NonNull String message, @Nullable Object invalidValue) {
        if (invalidValue != null) {
            return new InvalidFormatException(message, null, invalidValue);
        } else {
            return new SerdeException(message);
        }
    }

    /**
     * Decodes Oracle JSON binary data as byte array.
     *
     * @return the byte array for Oracle JSON binary
     */
    public byte[] decodeBinary() {
        if (currentEvent == OracleJsonParser.Event.VALUE_BINARY) {
            byte[] bytes = jsonParser.getBytes();
            nextToken();
            return bytes;
        }
        if (currentEvent == OracleJsonParser.Event.START_ARRAY) {
            OracleJsonArray oracleJsonArray = jsonParser.getArray();
            int size = oracleJsonArray.size();
            byte[] bytes = new byte[size];
            for (int i = 0; i < size; i++) {
                if (oracleJsonArray.isNull(i)) {
                    bytes[i] = 0;
                } else {
                    bytes[i] = (byte) oracleJsonArray.getInt(i);
                }
            }
            nextToken();
            return bytes;
        }
        if (currentEvent == OracleJsonParser.Event.VALUE_STRING) {
            String str = jsonParser.getString();
            nextToken();
            // string binary representation is Base16 encoded so we should decode it
            return decodeBase16(str);
        }
        throw new IllegalStateException(METHOD_CALLED_IN_WRONG_CONTEXT + currentEvent);
    }

    /**
     * Decodes Oracle JSON value as {@link LocalDate}.
     *
     * @return the {@link LocalDate} value being decoded
     */
    public LocalDate decodeLocalDate() {
        LocalDate value =
            switch (currentEvent) {
                case VALUE_DATE -> jsonParser.getLocalDateTime().toLocalDate();
                case VALUE_STRING -> LocalDate.parse(jsonParser.getString());
                default -> throw new IllegalStateException(METHOD_CALLED_IN_WRONG_CONTEXT + currentEvent);
            };
        nextToken();
        return value;
    }

    /**
     * Decodes Oracle JSON value as {@link LocalDateTime}.
     *
     * @return the {@link LocalDateTime} value being decoded
     */
    public LocalDateTime decodeLocalDateTime() {
        LocalDateTime value =
            switch (currentEvent) {
                case VALUE_DATE, VALUE_TIMESTAMP -> jsonParser.getLocalDateTime();
                case VALUE_STRING -> LocalDateTime.parse(jsonParser.getString());
                default -> throw new IllegalStateException(METHOD_CALLED_IN_WRONG_CONTEXT + currentEvent);
            };
        nextToken();
        return value;
    }

    /**
     * Decodes Oracle JSON value as {@link OffsetDateTime}.
     *
     * @return the {@link OffsetDateTime} value being decoded
     */
    public OffsetDateTime decodeOffsetDateTime() {
        OffsetDateTime value =
            switch (currentEvent) {
                case VALUE_TIMESTAMPTZ -> jsonParser.getOffsetDateTime();
                case VALUE_STRING -> OffsetDateTime.parse(jsonParser.getString());
                default -> throw new IllegalStateException(METHOD_CALLED_IN_WRONG_CONTEXT + currentEvent);
            };
        nextToken();
        return value;
    }

    /**
     * Decodes Oracle JSON value as {@link ZonedDateTime}.
     *
     * @return the {@link ZonedDateTime} value being decoded
     */
    public ZonedDateTime decodeZonedDateTime() {
        ZonedDateTime value =
            switch (currentEvent) {
                case VALUE_TIMESTAMPTZ -> jsonParser.getOffsetDateTime().toZonedDateTime();
                case VALUE_STRING -> ZonedDateTime.parse(jsonParser.getString());
                default -> throw new IllegalStateException(METHOD_CALLED_IN_WRONG_CONTEXT + currentEvent);
            };
        nextToken();
        return value;
    }

    private static byte[] decodeBase16(CharSequence cs) {
        final int numCh = cs.length();
        if ((numCh % 2) != 0) {
            throw new IllegalArgumentException("Encoded string must have an even length");
        }
        byte[] array = new byte[numCh / 2];
        for (int p = 0; p < numCh; p += 2) {
            int hi = Character.digit(cs.charAt(p), 16);
            int lo = Character.digit(cs.charAt(p + 1), 16);
            if ((hi | lo) < 0) {
                throw new IllegalArgumentException("Encoded string " + cs + " contains non-hex characters");
            }
            array[p / 2] = (byte) (hi << 4 | lo);
        }
        return array;
    }
}
