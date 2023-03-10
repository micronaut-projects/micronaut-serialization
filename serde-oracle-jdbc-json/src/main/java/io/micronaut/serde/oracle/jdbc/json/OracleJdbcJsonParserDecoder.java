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
import io.micronaut.core.util.StringUtils;
import io.micronaut.serde.exceptions.InvalidFormatException;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.AbstractStreamDecoder;
import oracle.sql.json.OracleJsonBinary;
import oracle.sql.json.OracleJsonParser;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Implementation of the {@link io.micronaut.serde.Decoder} interface for Oracle JDBC JSON.
 *
 * @author Denis Stepanov
 * @since 1.2.0
 */
@Internal
final class OracleJdbcJsonParserDecoder extends AbstractStreamDecoder {
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
        switch (currentEvent) {
            case START_ARRAY:
                return TokenType.START_ARRAY;
            case START_OBJECT:
                return TokenType.START_OBJECT;
            case KEY_NAME:
                return TokenType.KEY;
            case VALUE_STRING:
                return TokenType.STRING;
            case VALUE_DECIMAL, VALUE_DOUBLE, VALUE_FLOAT:
                return TokenType.NUMBER;
            case VALUE_TRUE, VALUE_FALSE:
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
            case VALUE_STRING, VALUE_DECIMAL, VALUE_DOUBLE, VALUE_FLOAT, VALUE_INTERVALDS,
                VALUE_INTERVALYM, VALUE_TIMESTAMPTZ, VALUE_DATE:
                // only allowed for string, number
                // additionally for processing string values from VALUE_INTERVALDS, VALUE_INTERVALYM, VALUE_TIMESTAMP,
                // VALUE_TIMESTAMPTZ and VALUE_DATE
                // in combination with custom de/serializers configured for Oracle Json parsing
                return jsonParser.getString();
            case VALUE_TIMESTAMP:
                return jsonParser.getLocalDateTime().toString();
            case VALUE_TRUE:
                return StringUtils.TRUE;
            case VALUE_FALSE:
                return StringUtils.FALSE;
            case VALUE_BINARY:
                // This is used to parse metadata _etag from Oracle Json View which is treated as binary value
                // and getString() returns what we need.
                OracleJsonBinary binary = jsonParser.getValue().asJsonBinary();
                return binary.getString();
             default:
                throw new IllegalStateException("Method called in wrong context " + currentEvent);
        }
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
        switch (currentEvent) {
            case VALUE_DECIMAL:
                return jsonParser.getLong();
            case VALUE_DOUBLE:
                return jsonParser.getDouble();
            case VALUE_FLOAT:
                return jsonParser.getFloat();
            default:
                throw new IllegalStateException("Method called in wrong context " + currentEvent);
        }
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
    public IOException createDeserializationException(String message, Object invalidValue) {
        if (invalidValue != null) {
            return new InvalidFormatException(message, null, invalidValue);
        } else {
            return new SerdeException(message);
        }
    }
}
