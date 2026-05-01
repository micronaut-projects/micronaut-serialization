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
import io.micronaut.serde.exceptions.SerdeException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.EOFException;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 *
 */
public abstract sealed class XmlReaderDecoder extends LimitingStream implements Decoder
        permits XmlReaderDecoder.DocumentDecoder, XmlReaderDecoder.ObjectDecoder, XmlReaderDecoder.ArrayDecoder {

    final Cursor cursor;

    XmlReaderDecoder(@NonNull RemainingLimits limits, @NonNull Cursor cursor) {
        super(limits);
        this.cursor = cursor;
    }

    // default Decoder surface

    @Override
    public @NonNull Decoder decodeArray(Argument<?> type) throws IOException {
        throw createDeserializationException("Array decoding not supported in current XML decoder.", null);
    }

    @Override
    public boolean hasNextArrayValue() throws IOException {
        return false;
    }

    @Override
    public @Nullable String decodeKey() throws IOException {
        throw new IllegalStateException("decodeKey() called outside of an object scope");
    }

    @Override
    public @NonNull String decodeString() throws IOException {
        throw new IllegalStateException("No scalar value available at current XML position");
    }

    @Override
    public boolean decodeBoolean() throws IOException {
        return Boolean.parseBoolean(decodeString().trim());
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
        String s = decodeString();
        if (s.isEmpty()) {
            throw createDeserializationException("Empty XML text cannot be decoded as char", s);
        }
        return s.charAt(0);
    }

    @Override
    public int decodeInt() throws IOException {
        String s = decodeString().trim();
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return new BigDecimal(s).intValueExact();
        }
    }

    @Override
    public long decodeLong() throws IOException {
        String s = decodeString().trim();
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return new BigDecimal(s).longValueExact();
        }
    }

    @Override
    public float decodeFloat() throws IOException {
        return Float.parseFloat(decodeString().trim());
    }

    @Override
    public double decodeDouble() throws IOException {
        return Double.parseDouble(decodeString().trim());
    }

    @Override
    public @NonNull BigInteger decodeBigInteger() throws IOException {
        String s = decodeString().trim();
        try {
            return new BigInteger(s);
        } catch (NumberFormatException e) {
            return new BigDecimal(s).toBigIntegerExact();
        }
    }

    @Override
    public @NonNull BigDecimal decodeBigDecimal() throws IOException {
        return new BigDecimal(decodeString().trim());
    }

    @Override
    public boolean decodeNull() throws IOException {
        return false;
    }

    @Override
    public @Nullable Object decodeArbitrary() throws IOException {
        throw createDeserializationException("decodeArbitrary() not supported in current XML decoder.", null);
    }

    @Override
    public @NonNull JsonNode decodeNode() throws IOException {
        throw createDeserializationException("decodeNode() not supported in current XML decoder.", null);
    }

    @Override
    public Decoder decodeBuffer() throws IOException {
        throw createDeserializationException("decodeBuffer() not supported in current XML decoder.", null);
    }

    @Override
    public void skipValue() throws IOException {
        throw new IllegalStateException("skipValue() called outside of an object scope");
    }

    @Override
    public void finishStructure(boolean consumeLeftElements) throws IOException {
        // root-level: nothing to finish
    }

    @Override
    public @NonNull IOException createDeserializationException(@NonNull String message, @Nullable Object invalidValue) {
        return new SerdeException(message + (invalidValue == null ? "" : " (value: " + invalidValue + ")"));
    }

    /**
     *
     */
    static final class Cursor {
        private final XMLStreamReader reader;

        Cursor(XMLStreamReader reader) {
            this.reader = reader;
        }

        int current() {
            return reader.getEventType();
        }

        String localName() {
            return reader.getLocalName();
        }

        String text() {
            return reader.getText();
        }

        /** Advance to the next significant event and return its type. */
        int advance() throws IOException {
            try {
                while (reader.hasNext()) {
                    int e = reader.next();
                    switch (e) {
                        case XMLStreamConstants.COMMENT:
                        case XMLStreamConstants.PROCESSING_INSTRUCTION:
                        case XMLStreamConstants.DTD:
                        case XMLStreamConstants.ENTITY_REFERENCE:
                            continue;
                        default:
                            return e;
                    }
                }
                return XMLStreamConstants.END_DOCUMENT;
            } catch (XMLStreamException x) {
                throw new IOException("XML stream error", x);
            }
        }
    }

    public static final class DocumentDecoder extends XmlReaderDecoder {

        private boolean rootConsumed;

        public DocumentDecoder(@NonNull RemainingLimits limits, @NonNull XMLStreamReader reader) throws IOException {
            super(limits, new Cursor(reader));
            // advance to the first START_ELEMENT
            int e = cursor.current();
            while (e != XMLStreamConstants.START_ELEMENT) {
                if (e == XMLStreamConstants.END_DOCUMENT) {
                    throw createDeserializationException("XML document is empty (no root element)", null);
                }
                e = cursor.advance();
            }
        }

        @Override
        public @NonNull Decoder decodeObject(@NonNull Argument<?> type) throws IOException {
            if (rootConsumed) {
                throw new IllegalStateException("XML root already consumed");
            }
            String name = cursor.localName();
            cursor.advance(); // consume START_ELEMENT, cursor now inside root element
            rootConsumed = true;
            return new ObjectDecoder(childLimits(), cursor, name);
        }

        @Override
        public @NonNull String decodeString() throws IOException {
            // top-level scalar document like <root>text</root>
            if (rootConsumed) {
                throw new IllegalStateException("XML root already consumed");
            }
            cursor.advance(); // consume START_ELEMENT
            rootConsumed = true;
            StringBuilder sb = new StringBuilder();
            while (true) {
                int e = cursor.current();
                if (e == XMLStreamConstants.CHARACTERS || e == XMLStreamConstants.CDATA || e == XMLStreamConstants.SPACE) {
                    sb.append(cursor.text());
                    cursor.advance();
                } else if (e == XMLStreamConstants.END_ELEMENT) {
                    cursor.advance();
                    return sb.toString();
                } else if (e == XMLStreamConstants.END_DOCUMENT) {
                    return sb.toString();
                } else {
                    throw createDeserializationException("Unexpected XML event " + e + " in scalar root", null);
                }
            }
        }
    }

    public static final class ObjectDecoder extends XmlReaderDecoder {

        private final String ownerElement;
        /** Local name of the current key/value element returned by the most recent decodeKey(). */
        private @Nullable String currentKey;
        /** True after we've consumed the owner's END_ELEMENT in finishStructure. */
        private boolean finished;

        ObjectDecoder(@NonNull RemainingLimits limits, @NonNull Cursor cursor, @NonNull String ownerElement) {
            super(limits, cursor);
            this.ownerElement = ownerElement;
        }

        @Override
        public @Nullable String decodeKey() throws IOException {
            if (finished) {
                return null;
            }
            // Skip whitespace text between elements; stop on START_ELEMENT or matching END_ELEMENT.
            while (true) {
                int e = cursor.current();
                switch (e) {
                    case XMLStreamConstants.START_ELEMENT:
                        currentKey = cursor.localName();
                        cursor.advance(); // consume START_ELEMENT, cursor now inside the value element
                        return currentKey;
                    case XMLStreamConstants.END_ELEMENT:
                        // Owner end — caller will call finishStructure
                        return null;
                    case XMLStreamConstants.CHARACTERS:
                    case XMLStreamConstants.CDATA:
                    case XMLStreamConstants.SPACE:
                        cursor.advance();
                        continue;
                    case XMLStreamConstants.END_DOCUMENT:
                        return null;
                    default:
                        cursor.advance();
                }
            }
        }

        @Override
        public @NonNull String decodeString() throws IOException {
            requireKey();
            StringBuilder sb = new StringBuilder();
            while (true) {
                int e = cursor.current();
                switch (e) {
                    case XMLStreamConstants.CHARACTERS:
                    case XMLStreamConstants.CDATA:
                    case XMLStreamConstants.SPACE:
                        sb.append(cursor.text());
                        cursor.advance();
                        break;
                    case XMLStreamConstants.END_ELEMENT:
                        cursor.advance(); // consume value's END_ELEMENT
                        currentKey = null;
                        return sb.toString();
                    case XMLStreamConstants.START_ELEMENT:
                        throw createDeserializationException(
                                "Expected scalar text for <" + currentKey + "> but found nested <" + cursor.localName() + ">",
                                cursor.localName());
                    case XMLStreamConstants.END_DOCUMENT:
                        throw new EOFException("Unexpected end of XML document while reading <" + currentKey + ">");
                    default:
                        cursor.advance();
                }
            }
        }

        @Override
        public boolean decodeNull() throws IOException {
            // Empty-element coercion: <x/> or <x></x> reads as null.
            int e = cursor.current();
            if (e == XMLStreamConstants.END_ELEMENT) {
                cursor.advance();
                currentKey = null;
                return true;
            }
            return false;
        }

        @Override
        public @NonNull Decoder decodeObject(@NonNull Argument<?> type) throws IOException {
            requireKey();
            String childOwner = currentKey;
            currentKey = null;
            return new ObjectDecoder(childLimits(), cursor, childOwner);
        }

        @Override
        public @NonNull Decoder decodeArray(Argument<?> type) throws IOException {
            requireKey();
            String wrapper = currentKey;
            currentKey = null;
            return new ArrayDecoder(childLimits(), cursor, wrapper);
        }

        @Override
        public void skipValue() throws IOException {
            // cursor is positioned inside the value element (after its START_ELEMENT was consumed by decodeKey).
            int depth = 1;
            while (depth > 0) {
                int e = cursor.current();
                if (e == XMLStreamConstants.START_ELEMENT) {
                    depth++;
                } else if (e == XMLStreamConstants.END_ELEMENT) {
                    depth--;
                    if (depth == 0) {
                        cursor.advance(); // consume the matching END_ELEMENT
                        break;
                    }
                } else if (e == XMLStreamConstants.END_DOCUMENT) {
                    throw new EOFException("Unexpected end of XML document while skipping <" + currentKey + ">");
                }
                cursor.advance();
            }
            currentKey = null;
        }

        @Override
        public void finishStructure(boolean consumeLeftElements) throws IOException {
            if (finished) {
                return;
            }
            if (consumeLeftElements) {
                String key;
                while ((key = decodeKey()) != null) {
                    // drain remaining children
                    skipValue();
                }
            } else {
                // Verify cursor is at END_ELEMENT of owner — if not, leftover content remains.
                int e = cursor.current();
                if (e != XMLStreamConstants.END_ELEMENT) {
                    throw new IllegalStateException(
                            "Unconsumed XML elements remain in <" + ownerElement + "> (event " + e + ")");
                }
            }
            // consume the owner's END_ELEMENT
            int e = cursor.current();
            if (e == XMLStreamConstants.END_ELEMENT) {
                cursor.advance();
            }
            finished = true;
        }

        private void requireKey() {
            if (currentKey == null) {
                throw new IllegalStateException("No XML element currently open for value decoding (call decodeKey first)");
            }
        }
    }

    /**
     *
     */
    public static final class ArrayDecoder extends XmlReaderDecoder {

        private final String wrapperElement;
        private boolean itemPending;
        private @Nullable String currentItemName;
        private boolean finished;

        ArrayDecoder(@NonNull RemainingLimits limits, @NonNull Cursor cursor, @NonNull String wrapperElement) {
            super(limits, cursor);
            this.wrapperElement = wrapperElement;
        }

        @Override
        public boolean hasNextArrayValue() throws IOException {
            if (finished) {
                return false;
            }
            if (itemPending) {
                return true;
            }
            while (true) {
                int e = cursor.current();
                switch (e) {
                    case XMLStreamConstants.START_ELEMENT:
                        currentItemName = cursor.localName();
                        cursor.advance(); // consume item's START_ELEMENT
                        itemPending = true;
                        return true;
                    case XMLStreamConstants.END_ELEMENT:
                        return false; // wrapper close
                    case XMLStreamConstants.CHARACTERS:
                    case XMLStreamConstants.CDATA:
                    case XMLStreamConstants.SPACE:
                        cursor.advance();
                        continue;
                    case XMLStreamConstants.END_DOCUMENT:
                        return false;
                    default:
                        cursor.advance();
                }
            }
        }

        @Override
        public boolean decodeNull() throws IOException {
            if (!itemPending) {
                return false;
            }
            int e = cursor.current();
            if (e == XMLStreamConstants.END_ELEMENT) {
                cursor.advance(); // consume item's END_ELEMENT
                clearItem();
                return true;
            }
            return false;
        }

        @Override
        public @NonNull Decoder decodeObject(@NonNull Argument<?> type) throws IOException {
            requireItem();
            String name = currentItemName;
            clearItem();
            return new ObjectDecoder(childLimits(), cursor, name);
        }

        @Override
        public @NonNull Decoder decodeArray(Argument<?> type) throws IOException {
            requireItem();
            String name = currentItemName;
            clearItem();
            return new ArrayDecoder(childLimits(), cursor, name);
        }

        @Override
        public @NonNull String decodeString() throws IOException {
            requireItem();
            StringBuilder sb = new StringBuilder();
            while (true) {
                int e = cursor.current();
                switch (e) {
                    case XMLStreamConstants.CHARACTERS:
                    case XMLStreamConstants.CDATA:
                    case XMLStreamConstants.SPACE:
                        sb.append(cursor.text());
                        cursor.advance();
                        break;
                    case XMLStreamConstants.END_ELEMENT:
                        cursor.advance(); // consume item's END_ELEMENT
                        clearItem();
                        return sb.toString();
                    case XMLStreamConstants.START_ELEMENT:
                        throw createDeserializationException(
                                "Expected scalar array item but found nested <" + cursor.localName() + ">",
                                cursor.localName());
                    case XMLStreamConstants.END_DOCUMENT:
                        throw new EOFException("Unexpected end of XML document while reading array item <" + currentItemName + ">");
                    default:
                        cursor.advance();
                }
            }
        }

        @Override
        public void skipValue() throws IOException {
            if (!itemPending) {
                return;
            }
            int depth = 1;
            while (depth > 0) {
                int e = cursor.current();
                if (e == XMLStreamConstants.START_ELEMENT) {
                    depth++;
                } else if (e == XMLStreamConstants.END_ELEMENT) {
                    depth--;
                    if (depth == 0) {
                        cursor.advance(); // consume the matching END_ELEMENT
                        break;
                    }
                } else if (e == XMLStreamConstants.END_DOCUMENT) {
                    throw new EOFException("Unexpected end of XML document while skipping array item");
                }
                cursor.advance();
            }
            clearItem();
        }

        @Override
        public @Nullable String decodeKey() throws IOException {
            // Arrays expose no keys.
            return null;
        }

        @Override
        public void finishStructure(boolean consumeLeftElements) throws IOException {
            if (finished) {
                return;
            }
            if (itemPending) {
                if (consumeLeftElements) {
                    skipValue();
                } else {
                    throw new IllegalStateException("Array item pending in <" + wrapperElement + ">");
                }
            }
            if (consumeLeftElements) {
                while (hasNextArrayValue()) {
                    skipValue();
                }
            }
            int e = cursor.current();
            if (e != XMLStreamConstants.END_ELEMENT) {
                if (consumeLeftElements) {
                    // drain remaining content until END_ELEMENT of wrapper
                    while (cursor.current() != XMLStreamConstants.END_ELEMENT
                            && cursor.current() != XMLStreamConstants.END_DOCUMENT) {
                        cursor.advance();
                    }
                } else {
                    throw new IllegalStateException(
                            "Unconsumed XML content in array <" + wrapperElement + "> (event " + e + ")");
                }
            }
            if (cursor.current() == XMLStreamConstants.END_ELEMENT) {
                cursor.advance(); // consume wrapper END_ELEMENT
            }
            finished = true;
        }

        private void requireItem() {
            if (!itemPending) {
                throw new IllegalStateException("No array item is currently pending (call hasNextArrayValue first)");
            }
        }

        private void clearItem() {
            itemPending = false;
            currentItemName = null;
        }
    }
}
