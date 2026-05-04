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
import io.micronaut.serde.CoercedNullAwareDecoder;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Map;

/**
 *
 */
public abstract sealed class XmlReaderDecoder extends LimitingStream implements Decoder, CoercedNullAwareDecoder
        permits XmlReaderDecoder.DocumentDecoder,
                XmlReaderDecoder.ObjectDecoder,
                XmlReaderDecoder.ArrayDecoder,
                XmlReaderDecoder.SyntheticRootDecoder {

    @Override
    public boolean isCoercedNullValue() {
        return false;
    }

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
    static @Nullable Object readArbitraryValue(@NonNull Cursor cursor) throws IOException {
        StringBuilder text = null;
        while (true) {
            int e = cursor.current();
            switch (e) {
                case XMLStreamConstants.CHARACTERS:
                case XMLStreamConstants.CDATA:
                case XMLStreamConstants.SPACE:
                    if (text == null) {
                        text = new StringBuilder();
                    }
                    text.append(cursor.text());
                    cursor.advance();
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    cursor.advance(); // consume the element's END_ELEMENT
                    if (text == null) {
                        return null;
                    }
                    return text.toString();
                case XMLStreamConstants.START_ELEMENT:
                    return readArbitraryObject(cursor);
                case XMLStreamConstants.END_DOCUMENT:
                    return text == null ? null : text.toString();
                default:
                    cursor.advance();
            }
        }
    }

    private static Map<String, Object> readArbitraryObject(@NonNull Cursor cursor) throws IOException {
        Map<String, Object> map = new LinkedHashMap<>();
        while (true) {
            int e = cursor.current();
            switch (e) {
                case XMLStreamConstants.START_ELEMENT:
                    String childName = cursor.localName();
                    cursor.advance(); // consume child START_ELEMENT
                    Object childValue = readArbitraryValue(cursor);
                    Object existing = map.get(childName);
                    if (existing == null && !map.containsKey(childName)) {
                        map.put(childName, childValue);
                    } else if (existing instanceof List<?>) {
                        @SuppressWarnings("unchecked")
                        List<Object> list = (List<Object>) existing;
                        list.add(childValue);
                    } else {
                        List<Object> list = new ArrayList<>(2);
                        list.add(existing);
                        list.add(childValue);
                        map.put(childName, list);
                    }
                    break;
                case XMLStreamConstants.END_ELEMENT:
                    cursor.advance(); // consume parent's END_ELEMENT
                    return map;
                case XMLStreamConstants.CHARACTERS:
                case XMLStreamConstants.CDATA:
                case XMLStreamConstants.SPACE:
                    cursor.advance(); // discard whitespace between children
                    break;
                case XMLStreamConstants.END_DOCUMENT:
                    return map;
                default:
                    cursor.advance();
            }
        }
    }

    /**
     *
     */
    /** Captured XML attribute name + value pair, surfaced to deserializers as object keys. */
    record XmlAttr(@NonNull String name, @NonNull String value) { }

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

        /**
         * Snapshot the attributes of the current {@code START_ELEMENT}. Must be called BEFORE
         * advancing past the element start, since {@link XMLStreamReader#getAttributeCount()}
         * is only valid at {@code START_ELEMENT}.
         */
        @NonNull List<XmlAttr> captureAttributes() {
            int n = reader.getAttributeCount();
            if (n == 0) {
                return Collections.emptyList();
            }
            List<XmlAttr> out = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                out.add(new XmlAttr(reader.getAttributeLocalName(i), reader.getAttributeValue(i)));
            }
            return out;
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
            // Untyped object → expose synthetic document wrapper whose only key is the root
            // element local name. Required by WrappedObjectDeserializer for @JsonRootName beans.
            if (type.equalsType(Argument.OBJECT_ARGUMENT)) {
                rootConsumed = true;
                return new SyntheticRootDecoder(childLimits(), cursor, name);
            }
            List<XmlAttr> attrs = cursor.captureAttributes();
            cursor.advance(); // consume START_ELEMENT, cursor now inside root element
            rootConsumed = true;
            return new ObjectDecoder(childLimits(), cursor, name, attrs);
        }

        @Override
        public @NonNull Decoder decodeArray(Argument<?> type) throws IOException {
            // Top-level array: treat the root element as the wrapper (e.g. <ArrayList><item>..</item></ArrayList>).
            if (rootConsumed) {
                throw new IllegalStateException("XML root already consumed");
            }
            String name = cursor.localName();
            cursor.advance(); // consume root START_ELEMENT, cursor now inside root
            rootConsumed = true;
            return new ArrayDecoder(childLimits(), cursor, name);
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
        /** Attributes captured from the owner element's {@code START_ELEMENT}, emitted as keys before child elements. */
        private final List<XmlAttr> attrs;
        private int attrIndex;

        /** Set after {@link #decodeKey} returned an attribute name; holds the attribute's value text. */
        private @Nullable String currentAttrValue;
        /** Local name of the current child element returned by {@link #decodeKey}, after its {@code START_ELEMENT} was consumed. */
        private @Nullable String currentKey;
        /** Attributes of the just-consumed child element; passed to a nested {@link ObjectDecoder} when the deserializer calls {@link #decodeObject}. */
        private List<XmlAttr> pendingChildAttrs = Collections.emptyList();
        /** True after {@link #finishStructure} consumed the owner's {@code END_ELEMENT}. */
        private boolean finished;

        ObjectDecoder(@NonNull RemainingLimits limits, @NonNull Cursor cursor, @NonNull String ownerElement) {
            this(limits, cursor, ownerElement, Collections.emptyList());
        }

        ObjectDecoder(@NonNull RemainingLimits limits, @NonNull Cursor cursor, @NonNull String ownerElement, @NonNull List<XmlAttr> attrs) {
            super(limits, cursor);
            this.ownerElement = ownerElement;
            this.attrs = attrs;
        }

        @Override
        public @Nullable String decodeKey() throws IOException {
            if (finished) {
                return null;
            }
            // Emit attributes first; their value sits in currentAttrValue until consumed via a scalar decode.
            if (attrIndex < attrs.size()) {
                XmlAttr a = attrs.get(attrIndex++);
                currentAttrValue = a.value();
                currentKey = a.name();
                return a.name();
            }
            // Move on to child elements.
            while (true) {
                int e = cursor.current();
                switch (e) {
                    case XMLStreamConstants.START_ELEMENT:
                        currentKey = cursor.localName();
                        pendingChildAttrs = cursor.captureAttributes();
                        cursor.advance(); // consume child START_ELEMENT, cursor now inside child element
                        return currentKey;
                    case XMLStreamConstants.END_ELEMENT:
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
            if (currentAttrValue != null) {
                String v = currentAttrValue;
                clearKeyState();
                return v;
            }
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
                        clearKeyState();
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
            // Attribute key — attributes always carry text, never null.
            if (currentAttrValue != null) {
                return false;
            }
            // Empty-element coercion: <x/> or <x></x> reads as null.
            int e = cursor.current();
            if (e == XMLStreamConstants.END_ELEMENT) {
                cursor.advance();
                clearKeyState();
                return true;
            }
            return false;
        }

        @Override
        public boolean isCoercedNullValue() {
            // Attributes are never null-coerced; they always carry text.
            if (currentAttrValue != null) {
                return false;
            }
            // We're positioned just inside the just-consumed child START_ELEMENT;
            // an immediate END_ELEMENT means "<x/>" / "<x></x>" empty-element coercion.
            return cursor.current() == XMLStreamConstants.END_ELEMENT;
        }

        @Override
        public byte @NonNull [] decodeBinary() throws IOException {
            // XML idiomatically encodes binary as base64 text inside the element. Decode that
            // directly rather than going through the array-of-numbers default.
            String text = decodeString();
            if (text.isEmpty()) {
                return new byte[0];
            }
            try {
                return Base64.getDecoder().decode(text.trim());
            } catch (IllegalArgumentException ex) {
                throw createDeserializationException("Invalid base64 binary content: " + ex.getMessage(), text);
            }
        }

        @Override
        public @Nullable Object decodeArbitrary() throws IOException {
            requireKey();
            if (currentAttrValue != null) {
                String v = currentAttrValue;
                clearKeyState();
                return v;
            }
            Object v = readArbitraryValue(cursor);
            clearKeyState();
            return v;
        }

        @Override
        public @NonNull Decoder decodeObject(@NonNull Argument<?> type) throws IOException {
            requireKey();
            if (currentAttrValue != null) {
                throw createDeserializationException(
                        "Cannot decode XML attribute <@" + currentKey + "> as object", null);
            }
            String childOwner = Objects.requireNonNull(currentKey, "currentKey");
            List<XmlAttr> childAttrs = pendingChildAttrs;
            clearKeyState();
            return new ObjectDecoder(childLimits(), cursor, childOwner, childAttrs);
        }

        @Override
        public @NonNull Decoder decodeArray(Argument<?> type) throws IOException {
            requireKey();
            if (currentAttrValue != null) {
                throw createDeserializationException(
                        "Cannot decode XML attribute <@" + currentKey + "> as array", null);
            }
            String wrapper = Objects.requireNonNull(currentKey, "currentKey");
            clearKeyState();
            return new ArrayDecoder(childLimits(), cursor, wrapper);
        }

        @Override
        public void skipValue() throws IOException {
            if (currentAttrValue != null) {
                clearKeyState();
                return;
            }
            // cursor is inside the value element (after its START_ELEMENT was consumed by decodeKey).
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
            clearKeyState();
        }

        @Override
        public void finishStructure(boolean consumeLeftElements) throws IOException {
            if (finished) {
                return;
            }
            if (consumeLeftElements) {
                String key;
                while ((key = decodeKey()) != null) {
                    skipValue();
                }
            } else {
                int e = cursor.current();
                if (e != XMLStreamConstants.END_ELEMENT) {
                    throw new IllegalStateException(
                            "Unconsumed XML elements remain in <" + ownerElement + "> (event " + e + ")");
                }
            }
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

        private void clearKeyState() {
            currentKey = null;
            currentAttrValue = null;
            pendingChildAttrs = Collections.emptyList();
        }
    }

    /**
     *
     */
    public static final class ArrayDecoder extends XmlReaderDecoder {

        private enum Mode {
            /** Standard case: caller-consumed element is a wrapper containing item children. */
            WRAPPED,
            /** {@code @JacksonXmlElementWrapper(useWrapping=false)} case: caller-consumed element
             *  IS the first array item; subsequent items are sibling elements with the same name. */
            INLINE
        }

        private final String wrapperElement;
        private final Mode mode;
        private boolean itemPending;
        private @Nullable String currentItemName;
        private List<XmlAttr> currentItemAttrs = Collections.emptyList();
        /** INLINE only: scalar text already drained from the first item; consumed by next decodeString. */
        private @Nullable String firstScalarText;
        /** INLINE only: true while the pre-cached first item is the next pending array value. */
        private boolean firstItemPending;
        private boolean finished;

        ArrayDecoder(@NonNull RemainingLimits limits, @NonNull Cursor cursor, @NonNull String wrapperOrItemElement) throws IOException {
            super(limits, cursor);
            this.wrapperElement = wrapperOrItemElement;
            // Auto-detect WRAPPED vs INLINE by peeking inside the just-entered element.
            // If the first non-whitespace event is a child START_ELEMENT, the element wraps items.
            // If it is text + END_ELEMENT, the element IS itself the first scalar item (inline list).
            StringBuilder bufferedText = null;
            Mode detected;
            while (true) {
                int e = cursor.current();
                if (e == XMLStreamConstants.CHARACTERS
                        || e == XMLStreamConstants.CDATA
                        || e == XMLStreamConstants.SPACE) {
                    if (bufferedText == null) {
                        bufferedText = new StringBuilder();
                    }
                    bufferedText.append(cursor.text());
                    cursor.advance();
                    continue;
                }
                if (e == XMLStreamConstants.START_ELEMENT) {
                    detected = Mode.WRAPPED;
                    break;
                }
                if (e == XMLStreamConstants.END_ELEMENT) {
                    if (bufferedText != null && !isBlank(bufferedText)) {
                        // The element we're inside contained scalar text — treat as the first inline item.
                        cursor.advance(); // consume that item's END_ELEMENT
                        this.firstScalarText = bufferedText.toString();
                        this.firstItemPending = true;
                        detected = Mode.INLINE;
                        break;
                    }
                    detected = Mode.WRAPPED;
                    break;
                }
                if (e == XMLStreamConstants.END_DOCUMENT) {
                    detected = Mode.WRAPPED;
                    break;
                }
                cursor.advance();
            }
            this.mode = detected;
        }

        private static boolean isBlank(CharSequence s) {
            for (int i = 0; i < s.length(); i++) {
                if (!Character.isWhitespace(s.charAt(i))) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public boolean hasNextArrayValue() throws IOException {
            if (finished) {
                return false;
            }
            if (firstItemPending || itemPending) {
                return true;
            }
            while (true) {
                int e = cursor.current();
                switch (e) {
                    case XMLStreamConstants.START_ELEMENT:
                        if (mode == Mode.INLINE) {
                            // In inline mode, only same-name siblings count as items.
                            if (!cursor.localName().equals(wrapperElement)) {
                                return false;
                            }
                        }
                        currentItemName = cursor.localName();
                        currentItemAttrs = cursor.captureAttributes();
                        cursor.advance(); // consume item's START_ELEMENT
                        itemPending = true;
                        return true;
                    case XMLStreamConstants.END_ELEMENT:
                        return false; // wrapper close (WRAPPED) or parent's end (INLINE)
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
            if (firstItemPending) {
                // The pre-cached first inline item carried scalar text — never null.
                return false;
            }
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
        public @Nullable Object decodeArbitrary() throws IOException {
            if (firstItemPending) {
                String v = firstScalarText == null ? "" : firstScalarText;
                firstScalarText = null;
                firstItemPending = false;
                return v;
            }
            requireItem();
            Object v = readArbitraryValue(cursor);
            clearItem();
            return v;
        }

        @Override
        public byte @NonNull [] decodeBinary() throws IOException {
            String text = decodeString();
            if (text.isEmpty()) {
                return new byte[0];
            }
            try {
                return Base64.getDecoder().decode(text.trim());
            } catch (IllegalArgumentException ex) {
                throw createDeserializationException("Invalid base64 binary content: " + ex.getMessage(), text);
            }
        }

        @Override
        public @NonNull Decoder decodeObject(@NonNull Argument<?> type) throws IOException {
            if (firstItemPending) {
                throw createDeserializationException(
                        "Inline array first item carried scalar text and cannot be decoded as object", firstScalarText);
            }
            requireItem();
            String name = Objects.requireNonNull(currentItemName, "currentItemName");
            List<XmlAttr> itemAttrs = currentItemAttrs;
            clearItem();
            return new ObjectDecoder(childLimits(), cursor, name, itemAttrs);
        }

        @Override
        public @NonNull Decoder decodeArray(Argument<?> type) throws IOException {
            if (firstItemPending) {
                throw createDeserializationException(
                        "Inline array first item carried scalar text and cannot be decoded as nested array", firstScalarText);
            }
            requireItem();
            String name = Objects.requireNonNull(currentItemName, "currentItemName");
            clearItem();
            return new ArrayDecoder(childLimits(), cursor, name);
        }

        @Override
        public @NonNull String decodeString() throws IOException {
            if (firstItemPending) {
                String v = firstScalarText == null ? "" : firstScalarText;
                firstScalarText = null;
                firstItemPending = false;
                return v;
            }
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
            if (firstItemPending) {
                firstItemPending = false;
                firstScalarText = null;
                return;
            }
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
            if (firstItemPending || itemPending) {
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
            // INLINE: the parent ObjectDecoder owns the surrounding END_ELEMENT — do NOT consume it.
            if (mode == Mode.INLINE) {
                finished = true;
                return;
            }
            int e = cursor.current();
            if (e != XMLStreamConstants.END_ELEMENT) {
                if (consumeLeftElements) {
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
            currentItemAttrs = Collections.emptyList();
        }
    }

    /**
     *
     */
    public static final class SyntheticRootDecoder extends XmlReaderDecoder {

        private final String rootName;
        private boolean keyEmitted;
        private boolean valueConsumed;
        private boolean finished;

        SyntheticRootDecoder(@NonNull RemainingLimits limits, @NonNull Cursor cursor, @NonNull String rootName) {
            super(limits, cursor);
            this.rootName = rootName;
        }

        @Override
        public @Nullable String decodeKey() throws IOException {
            if (!keyEmitted) {
                keyEmitted = true;
                return rootName;
            }
            return null;
        }

        @Override
        public @NonNull Decoder decodeObject(@NonNull Argument<?> type) throws IOException {
            if (!keyEmitted || valueConsumed) {
                throw new IllegalStateException("SyntheticRootDecoder.decodeObject called out of order");
            }
            valueConsumed = true;
            // Cursor is still at the root START_ELEMENT — capture attrs, then advance.
            List<XmlAttr> attrs = cursor.captureAttributes();
            cursor.advance();
            return new ObjectDecoder(childLimits(), cursor, rootName, attrs);
        }

        @Override
        public @NonNull String decodeString() throws IOException {
            // Treat root as scalar text element when caller asks for a plain string.
            if (!keyEmitted || valueConsumed) {
                throw new IllegalStateException("SyntheticRootDecoder.decodeString called out of order");
            }
            valueConsumed = true;
            cursor.advance(); // consume root START_ELEMENT
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
                        cursor.advance();
                        return sb.toString();
                    case XMLStreamConstants.END_DOCUMENT:
                        return sb.toString();
                    default:
                        cursor.advance();
                }
            }
        }

        @Override
        public boolean decodeNull() throws IOException {
            return false;
        }

        @Override
        public void skipValue() throws IOException {
            if (!keyEmitted || valueConsumed) {
                return;
            }
            valueConsumed = true;
            // Skip the entire root element subtree.
            int depth = 0;
            int e = cursor.current();
            while (true) {
                if (e == XMLStreamConstants.START_ELEMENT) {
                    depth++;
                } else if (e == XMLStreamConstants.END_ELEMENT) {
                    depth--;
                    if (depth == 0) {
                        cursor.advance();
                        return;
                    }
                } else if (e == XMLStreamConstants.END_DOCUMENT) {
                    return;
                }
                e = cursor.advance();
            }
        }

        @Override
        public void finishStructure(boolean consumeLeftElements) throws IOException {
            finished = true;
        }
    }
}
