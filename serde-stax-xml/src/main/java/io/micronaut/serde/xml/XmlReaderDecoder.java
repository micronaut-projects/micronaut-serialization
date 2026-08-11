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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Keys;
import io.micronaut.serde.KeysAwareDecoder;
import io.micronaut.serde.KeysSupport;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.exceptions.SerdeException;
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
 * Streaming {@link Decoder} over an {@link javax.xml.stream.XMLStreamReader}, with one concrete
 * variant per XML scope (document root, object element, array wrapper, synthetic root).
 *
 * @since 3.2
 */
@Internal
public abstract sealed class XmlReaderDecoder extends LimitingStream implements Decoder
            permits XmlReaderDecoder.DocumentDecoder,
                    XmlReaderDecoder.ObjectDecoder,
                    XmlReaderDecoder.ArrayDecoder,
                    XmlReaderDecoder.SyntheticRootDecoder {

    private static final String XSI_NS = "http://www.w3.org/2001/XMLSchema-instance";
    private static final int XML_KEYS_CONTRIBUTION_INDEX = KeysSupport.indexOf(new XmlKeysProvider());

    final Cursor cursor;
    /**
     * When {@code true}, an empty XML element with no content is
     * surfaced as {@code null} by {@link ObjectDecoder#decodeNull()} via
     * {@link XmlSerdeConfiguration.XmlReadFeature#EMPTY_ELEMENT_AS_NULL}.
     */
    final boolean emptyElementAsNull;

    /**
     * Creates a decoder scope over an existing XML cursor.
     *
     * <p>The {@code emptyElementAsNull} flag is propagated from the root document decoder to every
     * nested object, array, and synthetic-root decoder. Keeping it on the shared base scope avoids
     * repeatedly consulting configuration while decoding each property.</p>
     *
     * @param limits The remaining stream limits inherited from the parent decoder scope
     * @param cursor The shared cursor over the XML stream
     * @param emptyElementAsNull Whether empty XML elements should be reported as {@code null}
     */
    XmlReaderDecoder(RemainingLimits limits, Cursor cursor, boolean emptyElementAsNull) {
        super(limits);
        this.cursor = cursor;
        this.emptyElementAsNull = emptyElementAsNull;
    }

    @Override
    public Decoder decodeArray(Argument<?> type) throws IOException {
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
    public String decodeString() throws IOException {
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
    public byte [] decodeBinary() throws IOException {
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
    public BigInteger decodeBigInteger() throws IOException {
        String s = decodeString().trim();
        try {
            return new BigInteger(s);
        } catch (NumberFormatException e) {
            return new BigDecimal(s).toBigIntegerExact();
        }
    }

    @Override
    public BigDecimal decodeBigDecimal() throws IOException {
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
    public JsonNode decodeNode() throws IOException {
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
    }

    @Override
    public IOException createDeserializationException(String message, @Nullable Object invalidValue) {
        return new SerdeException(message + (invalidValue == null ? "" : " (value: " + invalidValue + ")"));
    }

    static @Nullable Object readArbitraryValue(Cursor cursor) throws IOException {
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
                    cursor.advance();
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

    final void skipCurrentElement(String operation) throws IOException {
        int depth = 1;
        while (depth > 0) {
            int e = cursor.current();
            if (e == XMLStreamConstants.START_ELEMENT) {
                depth++;
            } else if (e == XMLStreamConstants.END_ELEMENT) {
                depth--;
                if (depth == 0) {
                    cursor.advance();
                    return;
                }
            } else if (e == XMLStreamConstants.END_DOCUMENT) {
                throw new EOFException("Unexpected end of XML document while " + operation);
            }
            cursor.advance();
        }
    }

    private static Map<String, Object> readArbitraryObject(Cursor cursor) throws IOException {
        Map<String, Object> map = new LinkedHashMap<>();
        while (true) {
            int e = cursor.current();
            switch (e) {
                case XMLStreamConstants.START_ELEMENT:
                    String childName = cursor.localName();
                    cursor.advance();
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
                    cursor.advance();
                    return map;
                case XMLStreamConstants.CHARACTERS:
                case XMLStreamConstants.CDATA:
                case XMLStreamConstants.SPACE:
                    cursor.advance();
                    break;
                case XMLStreamConstants.END_DOCUMENT:
                    return map;
                default:
                    cursor.advance();
            }
        }
    }

    private static boolean isTextEvent(int event) {
        return event == XMLStreamConstants.CHARACTERS
            || event == XMLStreamConstants.CDATA
            || event == XMLStreamConstants.SPACE;
    }

    /**
     * Captured XML attribute name + value pair, surfaced to deserializers as object keys.
     * @param name
     * @param value
     */
    record XmlAttr(String name, String value) { }

    static final class Cursor {
        private final XMLStreamReader reader;
        private boolean lastCaptureXsiNilTrue;

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
         *
         * <p>Attributes from the XML namespace ({@link #XSI_NS}) — {@code xsi:nil}
         * are filtered out of the returned list. {@code xsi:nil="true"} is
         * exposed via {@link #lastCaptureXsiNilTrue()} so the calling decoder can treat the
         * element body as an explicit null per the XML schema convention.
         */
        List<XmlAttr> captureAttributes() {
            lastCaptureXsiNilTrue = false;
            int n = reader.getAttributeCount();
            if (n == 0) {
                return Collections.emptyList();
            }
            List<XmlAttr> out = null;
            for (int i = 0; i < n; i++) {
                String ns = reader.getAttributeNamespace(i);
                String localName = reader.getAttributeLocalName(i);
                String value = reader.getAttributeValue(i);
                if (XSI_NS.equals(ns)) {
                    if ("nil".equals(localName) && "true".equalsIgnoreCase(value.trim())) {
                        lastCaptureXsiNilTrue = true;
                    }
                    continue;
                }
                if (out == null) {
                    out = new ArrayList<>();
                }
                out.add(new XmlAttr(localName, value));
            }
            return out == null ? Collections.emptyList() : out;
        }

        boolean lastCaptureXsiNilTrue() {
            return lastCaptureXsiNilTrue;
        }

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
                throw new SerdeException("Error reading XML", x);
            }
        }
    }

    /**
     * Decoder for a complete XML document rooted at the current stream element.
     */
    static final class DocumentDecoder extends XmlReaderDecoder {

        private boolean rootConsumed;

        /**
         * Creates a document decoder and advances the cursor to the first root start element.
         *
         * <p>This is the entry point used by {@link XmlObjectMapper}. It owns the initial
         * {@link Cursor} creation and normalizes reader position so the first decode operation sees
         * the XML document root. Comments, processing instructions, DTDs, and entity references are
         * skipped by the cursor.</p>
         *
         * @param limits The root stream limits
         * @param reader The XML stream reader positioned anywhere before or at the document root
         * @param emptyElementAsNull Whether empty XML elements should be reported as {@code null}
         * @throws IOException If the reader cannot be advanced to a root element
         */
        public DocumentDecoder(RemainingLimits limits,
                               XMLStreamReader reader,
                               boolean emptyElementAsNull) throws IOException {
            super(limits, new Cursor(reader), emptyElementAsNull);
            int e = cursor.current();
            while (e != XMLStreamConstants.START_ELEMENT) {
                if (e == XMLStreamConstants.END_DOCUMENT) {
                    throw createDeserializationException("XML document is empty (no root element)", null);
                }
                e = cursor.advance();
            }
        }

        @Override
        public Decoder decodeObject(Argument<?> type) throws IOException {
            if (rootConsumed) {
                throw new IllegalStateException("XML root already consumed");
            }
            String name = cursor.localName();
            if (type.equalsType(Argument.OBJECT_ARGUMENT)) {
                rootConsumed = true;
                return new SyntheticRootDecoder(childLimits(), cursor, name, emptyElementAsNull);
            }
            List<XmlAttr> attrs = cursor.captureAttributes();
            cursor.advance();
            rootConsumed = true;
            return new ObjectDecoder(childLimits(), cursor, name, attrs, emptyElementAsNull);
        }

        @Override
        public Decoder decodeArray(Argument<?> type) throws IOException {
            if (rootConsumed) {
                throw new IllegalStateException("XML root already consumed");
            }
            String name = cursor.localName();
            cursor.advance();
            rootConsumed = true;
            return new ArrayDecoder(childLimits(), cursor, name, emptyElementAsNull);
        }

        @Override
        public String decodeString() throws IOException {
            if (rootConsumed) {
                throw new IllegalStateException("XML root already consumed");
            }
            cursor.advance();
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

    /**
     * Decoder for XML elements represented as object properties.
     */
    static final class ObjectDecoder extends XmlReaderDecoder implements KeysAwareDecoder {

        private final String ownerElement;
        private final List<XmlAttr> attrs;
        private int attrIndex;

        private @Nullable String currentAttrValue;
        private @Nullable String currentKey;
        private @Nullable String pendingUnknownKey;
        private @Nullable XmlKey currentXmlKey;
        private List<XmlAttr> pendingChildAttrs = Collections.emptyList();
        private boolean pendingChildXsiNil;
        private boolean finished;

        /**
         * Creates an object decoder for a single XML element.
         *
         * <p>The cursor is expected to be positioned after the owner's {@code START_ELEMENT}; the
         * owner attributes are passed separately because StAX attributes are only available while
         * the cursor is on that start event. Attribute values are emitted first from
         * {@link #decodeKey()}, followed by child elements in stream order.</p>
         *
         * @param limits The limits for this object scope
         * @param cursor The shared cursor positioned inside the owner element
         * @param ownerElement The local name of the element represented by this object decoder
         * @param attrs Attributes captured from the owner element before its body was entered
         * @param emptyElementAsNull Whether empty XML elements should be reported as {@code null}
         */
        ObjectDecoder(RemainingLimits limits,
                      Cursor cursor,
                      String ownerElement,
                      List<XmlAttr> attrs,
                      boolean emptyElementAsNull) {
            super(limits, cursor, emptyElementAsNull);
            this.ownerElement = ownerElement;
            this.attrs = attrs;
        }

        @Override
        public @Nullable String decodeKey() throws IOException {
            if (pendingUnknownKey != null) {
                String key = pendingUnknownKey;
                pendingUnknownKey = null;
                return key;
            }
            if (finished) {
                return null;
            }
            currentXmlKey = null;
            if (attrIndex < attrs.size()) {
                XmlAttr a = attrs.get(attrIndex++);
                currentAttrValue = a.value();
                currentKey = a.name();
                return a.name();
            }
            while (true) {
                int e = cursor.current();
                switch (e) {
                    case XMLStreamConstants.START_ELEMENT:
                        currentKey = cursor.localName();
                        pendingChildAttrs = cursor.captureAttributes();
                        pendingChildXsiNil = cursor.lastCaptureXsiNilTrue();
                        cursor.advance();
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
        @SuppressWarnings("unchecked")
        public int decodeKey(Keys keys) throws IOException {
            if (pendingUnknownKey != null) {
                return MATCH_UNKNOWN_NAME;
            }
            Object[] xmlContribution = KeysSupport.get(keys, XML_KEYS_CONTRIBUTION_INDEX);
            int textKeyIndex = (int) xmlContribution[XmlKeysProvider.TEXT_KEY_INDEX];
            if (attrIndex >= attrs.size()
                && textKeyIndex != Keys.UNKNOWN_KEY
                && isTextEvent(cursor.current())) {
                currentXmlKey = ((XmlKey[]) xmlContribution[XmlKeysProvider.XML_KEYS_INDEX])[textKeyIndex];
                currentKey = currentXmlKey.name();
                return textKeyIndex;
            }
            String key = decodeKey();
            if (key == null) {
                return MATCH_END_OBJECT;
            }
            int keyIndex = keys.indexOf(key);
            if (keyIndex == Keys.UNKNOWN_KEY) {
                Map<String, Integer> inputNameIndexes =
                    (Map<String, Integer>) xmlContribution[XmlKeysProvider.INPUT_NAME_INDEXES_INDEX];
                keyIndex = inputNameIndexes.getOrDefault(
                    XmlKeysProvider.normalize(key, keys.caseInsensitive()),
                    Keys.UNKNOWN_KEY
                );
            }
            if (keyIndex == Keys.UNKNOWN_KEY) {
                pendingUnknownKey = key;
                return MATCH_UNKNOWN_NAME;
            }
            currentXmlKey = ((XmlKey[]) xmlContribution[XmlKeysProvider.XML_KEYS_INDEX])[keyIndex];
            return keyIndex;
        }

        @Override
        public String decodeString() throws IOException {
            if (currentAttrValue != null) {
                String value = currentAttrValue;
                clearKeyState();
                return value;
            }
            requireKey();
            boolean textProperty = currentXmlKey != null && currentXmlKey.text();
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
                        clearKeyState();
                        if (textProperty) {
                            finished = true;
                        }
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
            if (pendingChildXsiNil) {
                skipCurrentElement("draining xsi:nil element <" + currentKey + ">");
                clearKeyState();
                return true;
            }
            if (emptyElementAsNull && cursor.current() == XMLStreamConstants.END_ELEMENT) {
                cursor.advance();
                clearKeyState();
                return true;
            }
            return false;
        }

        @Override
        public @Nullable Object decodeArbitrary() throws IOException {
            requireKey();
            boolean textProperty = currentXmlKey != null && currentXmlKey.text();
            Object v = readArbitraryValue(cursor);
            clearKeyState();
            if (textProperty) {
                finished = true;
            }
            return v;
        }

        @Override
        public Decoder decodeObject(Argument<?> type) throws IOException {
            requireKey();
            String childOwner = Objects.requireNonNull(currentKey, "currentKey");
            List<XmlAttr> childAttrs = pendingChildAttrs;
            clearKeyState();
            return new ObjectDecoder(childLimits(), cursor, childOwner, childAttrs, emptyElementAsNull);
        }

        @Override
        public Decoder decodeArray(Argument<?> type) throws IOException {
            if (currentXmlKey != null && currentXmlKey.collectionLayout() == XmlCollectionLayout.INLINE) {
                requireKey();
                String itemName = Objects.requireNonNull(currentKey, "currentKey");
                List<XmlAttr> itemAttrs = pendingChildAttrs;
                clearKeyState();
                return new ArrayDecoder(childLimits(), cursor, itemName, itemAttrs, emptyElementAsNull);
            }
            requireKey();
            String wrapper = Objects.requireNonNull(currentKey, "currentKey");
            clearKeyState();
            return new ArrayDecoder(childLimits(), cursor, wrapper, emptyElementAsNull);
        }

        @Override
        public void skipValue() throws IOException {
            if (currentAttrValue != null) {
                clearKeyState();
                return;
            }
            if (currentXmlKey != null && currentXmlKey.text()) {
                skipTextProperty();
                return;
            }
            skipCurrentElement("skipping <" + currentKey + ">");
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

        private void skipTextProperty() throws IOException {
            while (true) {
                int event = cursor.current();
                if (isTextEvent(event)) {
                    cursor.advance();
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    cursor.advance();
                    clearKeyState();
                    finished = true;
                    return;
                } else if (event == XMLStreamConstants.END_DOCUMENT) {
                    clearKeyState();
                    finished = true;
                    return;
                } else {
                    throw createDeserializationException(
                        "Expected text content for " + currentKey + " but found XML event " + event,
                        null
                    );
                }
            }
        }

        private void clearKeyState() {
            currentKey = null;
            currentAttrValue = null;
            currentXmlKey = null;
            pendingChildAttrs = Collections.emptyList();
            pendingChildXsiNil = false;
        }
    }

    /**
     * Decoder for an XML array/collection. Auto-detects a wrapped list (a wrapper element whose
     * children are the items) versus an inline list
     * ({@code @JacksonXmlElementWrapper(useWrapping = false)}, where same-named sibling elements
     * are the items).
     */
    static final class ArrayDecoder extends XmlReaderDecoder {

        private enum Mode {
            WRAPPED,
            INLINE
        }

        private final String wrapperElement;
        private final Mode mode;
        private boolean itemPending;
        private @Nullable String currentItemName;
        private List<XmlAttr> currentItemAttrs = Collections.emptyList();
        private @Nullable String firstScalarText;
        private boolean firstItemPending;
        private boolean finished;

        /**
         * Creates an array decoder and detects whether the XML uses wrapped or inline array layout.
         *
         * <p>For wrapped arrays, the cursor starts inside the wrapper element and child start
         * elements become array items. For inline arrays, the cursor may start inside the first item
         * element and the decoder treats same-named sibling elements as subsequent items. Scalar
         * text encountered before a closing element is buffered as the first inline item.</p>
         *
         * @param limits The limits for this array scope
         * @param cursor The shared cursor positioned inside the wrapper or first item element
         * @param wrapperOrItemElement The local name of the wrapper element or inline item element
         * @param emptyElementAsNull Whether empty XML elements should be reported as {@code null}
         * @throws IOException If the cursor cannot be advanced while detecting array mode
         */
        ArrayDecoder(RemainingLimits limits,
                     Cursor cursor,
                     String wrapperOrItemElement,
                     boolean emptyElementAsNull) throws IOException {
            super(limits, cursor, emptyElementAsNull);
            this.wrapperElement = wrapperOrItemElement;
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
                        cursor.advance();
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

        /**
         * Creates an explicitly inline array whose first item start element was consumed by the
         * parent object decoder.
         *
         * @param limits The limits for this array scope
         * @param cursor The shared cursor positioned inside the first item element
         * @param itemElement The repeated inline item element name
         * @param firstItemAttrs Attributes captured from the first item start element
         * @param emptyElementAsNull Whether empty XML elements should be reported as {@code null}
         */
        ArrayDecoder(RemainingLimits limits,
                     Cursor cursor,
                     String itemElement,
                     List<XmlAttr> firstItemAttrs,
                     boolean emptyElementAsNull) {
            super(limits, cursor, emptyElementAsNull);
            this.wrapperElement = itemElement;
            this.mode = Mode.INLINE;
            this.itemPending = true;
            this.currentItemName = itemElement;
            this.currentItemAttrs = firstItemAttrs;
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
                            if (!cursor.localName().equals(wrapperElement)) {
                                return false;
                            }
                        }
                        currentItemName = cursor.localName();
                        currentItemAttrs = cursor.captureAttributes();
                        cursor.advance();
                        itemPending = true;
                        return true;
                    case XMLStreamConstants.END_ELEMENT:
                        return false;
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
        public Decoder decodeObject(Argument<?> type) throws IOException {
            if (firstItemPending) {
                throw createDeserializationException(
                        "Inline array first item carried scalar text and cannot be decoded as object", firstScalarText);
            }
            requireItem();
            String name = Objects.requireNonNull(currentItemName, "currentItemName");
            List<XmlAttr> itemAttrs = currentItemAttrs;
            clearItem();
            return new ObjectDecoder(childLimits(), cursor, name, itemAttrs, emptyElementAsNull);
        }

        @Override
        public Decoder decodeArray(Argument<?> type) throws IOException {
            if (firstItemPending) {
                throw createDeserializationException(
                        "Inline array first item carried scalar text and cannot be decoded as nested array", firstScalarText);
            }
            requireItem();
            String name = Objects.requireNonNull(currentItemName, "currentItemName");
            clearItem();
            return new ArrayDecoder(childLimits(), cursor, name, emptyElementAsNull);
        }

        @Override
        public String decodeString() throws IOException {
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
                        cursor.advance();
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
            skipCurrentElement("skipping array item");
            clearItem();
        }

        @Override
        public @Nullable String decodeKey() throws IOException {
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
                cursor.advance();
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
     * Decoder that exposes a single synthetic key (the XML root element's local name) followed by
     * its value. Used for untyped / {@code @JsonRootName} beans where the root element must be
     * surfaced as a wrapper property.
     */
    static final class SyntheticRootDecoder extends XmlReaderDecoder {

        private final String rootName;
        private boolean keyEmitted;
        private boolean valueConsumed;

        /**
         * Creates a decoder that exposes the document root name as a synthetic object key.
         *
         * <p>This scope is used when decoding untyped object values. XML has a required root
         * element, while map/object decoding expects a key-value pair. The synthetic decoder bridges
         * those models by returning the root element name from {@link #decodeKey()} and then
         * delegating the value to a normal {@link ObjectDecoder}.</p>
         *
         * @param limits The limits for this synthetic object scope
         * @param cursor The shared cursor positioned at the XML root element
         * @param rootName The root element local name to expose as the synthetic object key
         * @param emptyElementAsNull Whether empty XML elements should be reported as {@code null}
         */
        SyntheticRootDecoder(RemainingLimits limits,
                             Cursor cursor,
                             String rootName,
                             boolean emptyElementAsNull) {
            super(limits, cursor, emptyElementAsNull);
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
        public Decoder decodeObject(Argument<?> type) throws IOException {
            if (!keyEmitted || valueConsumed) {
                throw new IllegalStateException("SyntheticRootDecoder.decodeObject called out of order");
            }
            valueConsumed = true;
            List<XmlAttr> attrs = cursor.captureAttributes();
            cursor.advance();
            return new ObjectDecoder(childLimits(), cursor, rootName, attrs, emptyElementAsNull);
        }

        @Override
        public String decodeString() throws IOException {
            if (!keyEmitted || valueConsumed) {
                throw new IllegalStateException("SyntheticRootDecoder.decodeString called out of order");
            }
            valueConsumed = true;
            cursor.advance();
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
        public void skipValue() throws IOException {
            if (!keyEmitted || valueConsumed) {
                return;
            }
            valueConsumed = true;
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

    }
}
