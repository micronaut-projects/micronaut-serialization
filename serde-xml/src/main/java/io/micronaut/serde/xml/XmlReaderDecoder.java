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
package io.micronaut.serde.xml;

import io.micronaut.core.type.Argument;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.NamingStrategyLocator;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.util.JsonNodeDecoder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonToken;
import tools.jackson.dataformat.xml.deser.FromXmlParser;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

/**
 * Streaming XML implementation of {@link Decoder}.
 * <p>
 * Handles repeated sibling XML elements as arrays using name-tracked iteration:
 * after each value, checks whether the next PROPERTY_NAME matches the array
 * element name to decide if there are more items.
 * </p>
 *
 * <p>XML supported array pattern :</p>
 * <ul>
 *   <li><b>Pattern — wrapper container:</b>
 *       — enter the wrapper, then track inner elements by tag name.</li>
 * </ul>
 */
public class XmlReaderDecoder extends LimitingStream implements Decoder, NamingStrategyLocator {

    final FromXmlParser parser;
    /**
     * For name-tracked arrays: the XML tag name of the array elements.
     * {@link #hasNextArrayValue()} skips PROPERTY_NAMEs matching this name
     * and returns false when a different name (or END_OBJECT) is reached.
     * Null for object decoders and END_ARRAY-delimited arrays.
     */
    @Nullable
    private final String arrayElementName;
    /**
     * True for Pattern (wrapper container): {@link #finishStructure} must
     * consume the wrapper's END_OBJECT after the inner array ends.
     */
    private final boolean wrappedArray;
    /**
     * True for empty-wrapper arrays: {@link #hasNextArrayValue()} returns false immediately.
     */
    private final boolean done;

    public XmlReaderDecoder(FromXmlParser parser, @NonNull RemainingLimits remainingLimits) {
        this(parser, remainingLimits, false, false, null);
    }

    public XmlReaderDecoder(XmlReaderDecoder parent, @NonNull RemainingLimits remainingLimits) {
        this(parent.parser, remainingLimits, false, false, null);
    }

    private XmlReaderDecoder(FromXmlParser parser, @NonNull RemainingLimits remainingLimits,
                              boolean wrappedArray, boolean done, @Nullable String arrayElementName) {
        super(remainingLimits);
        this.parser = parser;
        this.wrappedArray = wrappedArray;
        this.done = done;
        this.arrayElementName = arrayElementName;
    }

    @Override
    public @NonNull Decoder decodeObject(@NonNull Argument<?> type) throws IOException {
        var jsonToken = parser.currentToken();
        if (parser.currentToken() != JsonToken.START_OBJECT) {
            throw new SerdeException("Expected object but got: " + parser.currentToken());
        }
        parser.nextToken(); // advance inside to first PROPERTY_NAME or END_OBJECT
        return new XmlReaderDecoder(parser, childLimits(), false, false, null);
    }

    @Override
    public @NonNull Decoder decodeArray(@NonNull Argument<?> type) throws IOException {
        JsonToken token = parser.currentToken();
        if (token == JsonToken.START_ARRAY) {
            // Real START_ARRAY — advance past it.
            parser.nextToken();
            return new XmlReaderDecoder(parser, childLimits(), false, false, null);
        }

        if (token == JsonToken.START_OBJECT) {
            // Pattern — wrapper container: enter it, track inner element name.
            parser.nextToken(); // first PROPERTY_NAME or END_OBJECT
            if (parser.currentToken() == JsonToken.END_OBJECT) {
                parser.nextToken();
                return new XmlReaderDecoder(parser, childLimits(), false, true, null);
            }
            String innerName = parser.currentName();
            parser.nextToken(); // PROPERTY_NAME → first value

            return new XmlReaderDecoder(parser, childLimits(), true, false, innerName);
        }

        throw new SerdeException("Expected START_OBJECT or START_ARRAY for array but got: " + token);
    }

    @Override
    public boolean hasNextArrayValue() throws IOException {

        if (done) {
            return false;
        }
        JsonToken token = parser.currentToken();

        if (token == JsonToken.END_ARRAY) {
            return false;
        }

        if (token == JsonToken.END_OBJECT) {
            return false;
        }

        // Name-tracked array: check if the next PROPERTY_NAME is another array element
        if (arrayElementName != null && token == JsonToken.PROPERTY_NAME) {
            String name = parser.currentName();
            if (arrayElementName.equals(name)) {
                // Same element repeats — skip the PROPERTY_NAME, advance to value
                parser.nextToken();
                return true;
            }
            // Different element name — array is done
            return false;
        }
        // Current token is a value (VALUE_STRING, START_OBJECT, etc.) — more items
        return true;
    }

    @Override
    public @Nullable String decodeKey() throws IOException {
        if (parser.currentToken() == JsonToken.END_OBJECT) {
            return null;
        }
        if (parser.currentToken() != JsonToken.PROPERTY_NAME) {
            throw new SerdeException("Expected property name but got: " + parser.currentToken());
        }
        String key = parser.currentName();
        parser.nextToken();
        return key;
    }

    @Override
    public @NonNull String decodeString() throws IOException {
        JsonToken token = parser.currentToken();
        String value = switch (token) {
            case VALUE_STRING, VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> parser.getString();
            case VALUE_TRUE -> "true";
            case VALUE_FALSE -> "false";
            default -> throw new SerdeException("Expected string value but got: " + token);
        };
        parser.nextToken();
        return value;
    }

    @Override
    public boolean decodeBoolean() throws IOException {
        JsonToken token = parser.currentToken();
        boolean value = switch (token) {
            case VALUE_TRUE -> true;
            case VALUE_FALSE -> false;
            case VALUE_STRING -> Boolean.parseBoolean(parser.getString());
            case VALUE_NUMBER_INT -> parser.getIntValue() != 0;
            default -> throw new SerdeException("Expected boolean value but got: " + token);
        };
        parser.nextToken();
        return value;
    }

    @Override
    public byte decodeByte() throws IOException {
        return (byte) decodeInt();
    }

    @Override
    public short decodeShort() throws IOException {
        return (short) decodeInt();
    }

    @Override
    public char decodeChar() throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_STRING) {
            String s = parser.getString();
            if (s.length() != 1) {
                throw new SerdeException("When decoding char value, must give a single character but got: " + s);
            }
            parser.nextToken();
            return s.charAt(0);
        }
        return (char) decodeInt();
    }

    @Override
    public int decodeInt() throws IOException {
        JsonToken token = parser.currentToken();
        int value = switch (token) {
            case VALUE_NUMBER_INT -> parser.getIntValue();
            case VALUE_NUMBER_FLOAT -> (int) parser.getDoubleValue();
            case VALUE_STRING -> {
                try {
                    yield Integer.parseInt(parser.getString());
                } catch (NumberFormatException e) {
                    throw new SerdeException("Unable to coerce string to integer: " + parser.getString());
                }
            }
            case VALUE_TRUE -> 1;
            case VALUE_FALSE -> 0;
            default -> throw new SerdeException("Expected integer value but got: " + token);
        };
        parser.nextToken();
        return value;
    }

    @Override
    public long decodeLong() throws IOException {
        JsonToken token = parser.currentToken();
        long value = switch (token) {
            case VALUE_NUMBER_INT -> parser.getLongValue();
            case VALUE_NUMBER_FLOAT -> (long) parser.getDoubleValue();
            case VALUE_STRING -> {
                try {
                    yield Long.parseLong(parser.getString());
                } catch (NumberFormatException e) {
                    throw new SerdeException("Unable to coerce string to long: " + parser.getString());
                }
            }
            case VALUE_TRUE -> 1L;
            case VALUE_FALSE -> 0L;
            default -> throw new SerdeException("Expected long value but got: " + token);
        };
        parser.nextToken();
        return value;
    }

    @Override
    public float decodeFloat() throws IOException {
        return (float) decodeDouble();
    }

    @Override
    public double decodeDouble() throws IOException {
        JsonToken token = parser.currentToken();
        double value = switch (token) {
            case VALUE_NUMBER_FLOAT -> parser.getDoubleValue();
            case VALUE_NUMBER_INT -> (double) parser.getLongValue();
            case VALUE_STRING -> {
                try {
                    yield Double.parseDouble(parser.getString());
                } catch (NumberFormatException e) {
                    throw new SerdeException("Unable to coerce string to double: " + parser.getString());
                }
            }
            case VALUE_TRUE -> 1.0;
            case VALUE_FALSE -> 0.0;
            default -> throw new SerdeException("Expected double value but got: " + token);
        };
        parser.nextToken();
        return value;
    }

    @Override
    public @NonNull BigInteger decodeBigInteger() throws IOException {
        JsonToken token = parser.currentToken();
        BigInteger value = switch (token) {
            case VALUE_NUMBER_INT -> parser.getBigIntegerValue();
            case VALUE_NUMBER_FLOAT -> parser.getDecimalValue().toBigInteger();
            case VALUE_STRING -> {
                try {
                    yield new BigInteger(parser.getString());
                } catch (NumberFormatException e) {
                    yield BigInteger.ZERO;
                }
            }
            case VALUE_TRUE -> BigInteger.ONE;
            case VALUE_FALSE -> BigInteger.ZERO;
            default -> throw new SerdeException("Expected BigInteger value but got: " + token);
        };
        parser.nextToken();
        return value;
    }

    @Override
    public @NonNull BigDecimal decodeBigDecimal() throws IOException {
        JsonToken token = parser.currentToken();
        BigDecimal value = switch (token) {
            case VALUE_NUMBER_FLOAT -> parser.getDecimalValue();
            case VALUE_NUMBER_INT -> new BigDecimal(parser.getBigIntegerValue());
            case VALUE_STRING -> {
                try {
                    yield new BigDecimal(parser.getString());
                } catch (NumberFormatException e) {
                    yield BigDecimal.ZERO;
                }
            }
            case VALUE_TRUE -> BigDecimal.ONE;
            case VALUE_FALSE -> BigDecimal.ZERO;
            default -> throw new SerdeException("Expected BigDecimal value but got: " + token);
        };
        parser.nextToken();
        return value;
    }

    @Override
    public boolean decodeNull() throws IOException {
        if (parser.currentToken() == JsonToken.VALUE_NULL) {
            parser.nextToken();
            return true;
        }
        // An empty XML element <foo></foo> produces VALUE_STRING "" from the Jackson XML parser.
        // Treat it as null so that empty repeated elements in object arrays deserialize as null
        if (parser.currentToken() == JsonToken.VALUE_STRING) {
            String s = parser.getString();
            if (s == null || s.isEmpty()) {
                parser.nextToken();
                return true;
            }
        }
        return false;
    }

    @Override
    public @io.micronaut.core.annotation.Nullable Object decodeArbitrary() throws IOException {
        return null;
    }

    @Override
    public @io.micronaut.core.annotation.NonNull JsonNode decodeNode() throws IOException {
        return null;
    }

    @Override
    public Decoder decodeBuffer() throws IOException {
        JsonNode node = decodeNode();
        return JsonNodeDecoder.create(node, ourLimits());
    }

    @Override
    public void skipValue() throws IOException {
        JsonToken token = parser.currentToken();
        if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
            parser.skipChildren();
        }
        parser.nextToken();
    }

    @Override
    public void finishStructure(boolean consumeLeftElements) throws IOException {
        if (done) {
            return;
        }
        if (consumeLeftElements) {
            JsonToken token = parser.currentToken();
            while (token != null && token != JsonToken.END_OBJECT && token != JsonToken.END_ARRAY) {
                // For name-tracked arrays, stop when a different PROPERTY_NAME appears
                if (arrayElementName != null && token == JsonToken.PROPERTY_NAME) {
                    if (!arrayElementName.equals(parser.currentName())) {
                        break;
                    }
                    parser.nextToken(); // skip the array element's repeated PROPERTY_NAME
                    token = parser.currentToken();
                    continue;
                }
                if (token == JsonToken.PROPERTY_NAME) {
                    parser.nextToken(); // skip property name for object fields
                }
                skipValue();
                token = parser.currentToken();
            }
        }

        if (arrayElementName != null && !wrappedArray) {
            return;
        }

        JsonToken token = parser.currentToken();
        if (token == JsonToken.END_OBJECT || token == JsonToken.END_ARRAY) {
            parser.nextToken();
        }

        if (wrappedArray && parser.currentToken() == JsonToken.END_OBJECT) {
            parser.nextToken();
        }
    }

    @Override
    public @NonNull IOException createDeserializationException(@NonNull String message, @Nullable Object invalidValue) {
        return new SerdeException(message + " \n at " + parser.currentLocation());
    }

    @Override
    public @NonNull <D extends PropertyNamingStrategy> D findNamingStrategy(@NonNull Class<? extends D> namingStrategyClass) throws SerdeException {
        throw new SerdeException("Naming strategy not supported in XmlReaderDecoder");
    }
}
