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
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Keys;
import io.micronaut.serde.KeysAwareEncoder;
import io.micronaut.serde.KeysSupport;
import io.micronaut.serde.XmlEncoder;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;
import org.jspecify.annotations.Nullable;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.Objects;

/**
 * An {@link Encoder} that serializes objects to XML using a StAX {@link XMLStreamWriter}.
 *
 * @since 3.2
 */
@Internal
public final class XmlGenerator implements KeysAwareEncoder, XmlEncoder {

    private static final int XML_KEYS_CONTRIBUTION_INDEX = KeysSupport.indexOf(new XmlKeysProvider());
    private static final String XSI_NS = "http://www.w3.org/2001/XMLSchema-instance";

    private final XMLStreamWriter xmlWriter;
    private final Deque<ContextProperties> propertyStack = new ArrayDeque<>();
    private boolean rootMapper;
    private final @Nullable String rootName;
    private final @Nullable String rootNamespace;
    private @Nullable String pendingRootNamespace;

    /**
     * Creates an XML encoder with a fallback document root for scalar values.
     *
     * @param xmlWriter The XML stream writer to receive encoded events
     * @param rootName The scalar document root, or {@code null} to use {@code value}
     */
    XmlGenerator(XMLStreamWriter xmlWriter, @Nullable String rootName) {
        this.xmlWriter = xmlWriter;
        this.rootName = rootName;
        this.rootNamespace = null;
        this.rootMapper = false;
    }

    private XmlGenerator(XMLStreamWriter xmlWriter, Boolean rootMapper) {
        this(xmlWriter, rootMapper, null);
    }

    private XmlGenerator(XMLStreamWriter xmlWriter, Boolean rootMapper, @Nullable String rootNamespace) {
        this.xmlWriter = xmlWriter;
        this.rootName = null;
        this.rootNamespace = rootNamespace;
        this.rootMapper = rootMapper;
        this.pendingRootNamespace = rootNamespace;
    }

    private XmlGenerator(XMLStreamWriter xmlWriter, Deque<ContextProperties> propertyStack) {
        this.xmlWriter = xmlWriter;
        this.rootName = null;
        this.rootNamespace = null;
        this.propertyStack.addAll(propertyStack);
        this.rootMapper = false;
    }

    @Override
    public Encoder encodeArray(Argument<?> type) throws IOException {
        try {
            if (propertyStack.isEmpty()) {
                encodeRootArray(type);
            } else {
                encodeNestedArray();
            }
        } catch (XMLStreamException e) {
            throw new SerdeException("Error writing XML", e);
        }
        return this;
    }

    @Override
    public Encoder encodeObject(Argument<?> type) throws IOException {
        String name = type.getSimpleName();
        if (type.equals(Argument.OBJECT_ARGUMENT)) {
            Boolean rootMapper = true;
            return new XmlGenerator(xmlWriter, rootMapper);
        }
        XmlGenerator rootGenerator = rootGenerator(type);
        if (rootGenerator != null) {
            return rootGenerator;
        }
        try {
            return encodeObjectFrame(name);
        } catch (XMLStreamException e) {
            throw new SerdeException("Error writing XML", e);
        }
    }

    @Override
    public void finishStructure() throws IOException {
        try {
            ContextProperties lastProperty = propertyStack.peekLast();
            switch (lastProperty) {
                case KeyFrame _ -> finishKeyFrame();
                case ObjectFrame objectFrame -> finishObjectFrame(objectFrame);
                case ArrayFrame arrayFrame -> finishArrayFrame(arrayFrame);
                case null -> {
                    assert  propertyStack.isEmpty() : "Root name mapping";
                }
                default -> throw new IllegalStateException("Unexpected value: " + lastProperty);
            }

        } catch (XMLStreamException e) {
            throw new SerdeException("Error writing XML", e);
        }
    }

    @Override
    public void encodeKey(String key) throws IOException {
        try {
            if (rootMapper) {
                propertyStack.addLast(new ObjectFrame(key));
                if (pendingRootNamespace != null) {
                    xmlWriter.writeStartElement("", key, pendingRootNamespace);
                    pendingRootNamespace = null;
                } else {
                    xmlWriter.writeStartElement(key);
                }
                return;
            }

            ensurePendingObjectElementStarted();

            ContextProperties last = propertyStack.peekLast();
            if (last instanceof KeyFrame of && of.consumed()) {
                propertyStack.removeLast();
            } else if (last instanceof KeyFrame of && !of.consumed()) {
                propertyStack.removeLast();
                propertyStack.addLast(new KeyFrame(of.key(), of.consumed(), key, of.objectWrappingKey(), of.namespace(), of.xmlKey()));
                return;
            }

            propertyStack.addLast(new KeyFrame(key, false, null, null, null, null));

        } catch (XMLStreamException e) {
            throw new SerdeException("Error writing XML", e);
        }
    }

    @Override
    public void encodeAttributeKey(String key) throws IOException {
        encodeKey(key);
        ContextProperties lastProperty = propertyStack.peekLast();
        if (lastProperty instanceof KeyFrame keyFrame && !keyFrame.consumed()) {
            propertyStack.removeLast();
            propertyStack.addLast(new KeyFrame(
                keyFrame.key(),
                false,
                keyFrame.arrayWrappingKey(),
                keyFrame.objectWrappingKey(),
                null,
                new XmlKey(key, null, true, false, false, false, false, XmlCollectionLayout.DEFAULT, null, null, null, XmlNullHandling.DEFAULT, XmlNullHandling.DEFAULT)
            ));
        }
    }

    @Override
    public void encodeKey(Keys keys, int index) throws IOException {
        Object[] xmlKeys = KeysSupport.get(keys, XML_KEYS_CONTRIBUTION_INDEX);
        XmlKey xmlKey = ((XmlKey[]) xmlKeys[XmlKeysProvider.XML_KEYS_INDEX])[index];
        encodeKey(xmlKey.name());
        ContextProperties lastProperty = propertyStack.peekLast();
        if (lastProperty instanceof KeyFrame keyFrame && !keyFrame.consumed()) {
            propertyStack.removeLast();
            propertyStack.addLast(new KeyFrame(
                keyFrame.key(),
                false,
                keyFrame.arrayWrappingKey(),
                keyFrame.objectWrappingKey(),
                xmlKey.namespace(),
                xmlKey
            ));
        }
    }

    private void ensurePendingObjectElementStarted() throws XMLStreamException {
        if (propertyStack.size() == 2
            && propertyStack.peekFirst() instanceof KeyFrame keyFrame
            && propertyStack.peekLast() instanceof ObjectFrame objectFrame
            && objectFrame.key() == null
            && !Boolean.TRUE.equals(keyFrame.objectWrappingKey())) {
            writeStartElement(keyFrame.namespace(), keyFrame.key());
            propertyStack.removeFirst();
            propertyStack.addFirst(new KeyFrame(keyFrame.key(), keyFrame.consumed(), keyFrame.arrayWrappingKey(), Boolean.TRUE, keyFrame.namespace(), keyFrame.xmlKey()));
        }
    }

    private void writeScalar(String data) throws IOException {
        try {
            ContextProperties lastProperty = propertyStack.peekLast();
            switch (lastProperty) {
                case KeyFrame keyFrame -> writeKeyScalar(keyFrame, data);
                case ArrayFrame arrayFrame -> writeArrayScalar(arrayFrame, data);
                case null -> writeRootScalar(data);
                default -> throw new IllegalStateException("Unexpected value in writeScalar(): " + lastProperty + "\t " + lastProperty.getClass().getName());
            }
        } catch (XMLStreamException e) {
            throw new SerdeException("Error writing XML", e);
        }
    }

    private void encodeNestedArray() throws IOException, XMLStreamException {
        ContextProperties lastFrame = propertyStack.peekLast();
        String lastProperty = lastFrame.key();
        if (lastFrame instanceof KeyFrame keyFrame && encodeKeyArray(keyFrame)) {
            return;
        }
        xmlWriter.writeStartElement(lastProperty);
        String itemNamespace;
        boolean cdata;
        XmlNullHandling itemNullHandling;
        switch (lastFrame) {
            case KeyFrame keyFrame -> {
                itemNamespace = keyFrame.namespace();
                switch (keyFrame.xmlKey()) {
                    case XmlKey xmlKey -> {
                        cdata = xmlKey.cdata();
                        itemNullHandling = xmlKey.nullHandling();
                    }
                    case null -> {
                        cdata = false;
                        itemNullHandling = XmlNullHandling.DEFAULT;
                    }
                }
            }
            case ArrayFrame arrayFrame -> {
                itemNamespace = arrayFrame.itemNamespace();
                cdata = false;
                itemNullHandling = XmlNullHandling.DEFAULT;
            }
            default -> throw new IllegalStateException("Unexpected array frame: " + lastFrame);
        }
        propertyStack.addLast(new ArrayFrame(lastProperty, null, itemNamespace, cdata, itemNullHandling, false, false));
    }

    private boolean encodeKeyArray(KeyFrame keyFrame) throws IOException, XMLStreamException {
        XmlKey xmlKey = keyFrame.xmlKey();
        boolean attribute = switch (xmlKey) {
            case XmlKey key -> key.attribute();
            case null -> false;
        };
        if (attribute) {
            throw new SerdeException("XML attributes cannot contain array values: " + keyFrame.key());
        }
        String arrayWrappingKey = keyFrame.arrayWrappingKey();
        if (arrayWrappingKey != null) {
            xmlWriter.writeStartElement(arrayWrappingKey);
            propertyStack.addLast(new ArrayFrame(keyFrame.key(), null, keyFrame.namespace(), false, XmlNullHandling.DEFAULT, false, false));
            return true;
        }
        return switch (xmlKey) {
            case XmlKey key when key.mixed() -> {
                propertyStack.removeLast();
                propertyStack.addLast(new ArrayFrame("", null, keyFrame.namespace(), key.cdata(), key.nullHandling(), true, false));
                yield true;
            }
            case XmlKey key when key.list() -> {
                writeStartElement(keyFrame.namespace(), keyFrame.key());
                propertyStack.addLast(new ArrayFrame(keyFrame.key(), null, keyFrame.namespace(), key.cdata(), key.nullHandling(), key.list(), false));
                yield true;
            }
            case XmlKey key -> switch (key.collectionLayout()) {
                case INLINE -> {
                    propertyStack.removeLast();
                    propertyStack.addLast(new ArrayFrame("", keyFrame.key(), keyFrame.namespace(), key.cdata(), key.nullHandling(), false, false));
                    yield true;
                }
                case WRAPPED -> {
                    String wrapperName = key.wrapperName() == null
                        ? Objects.requireNonNull(keyFrame.key())
                        : key.wrapperName();
                    writeStartElement(key.wrapperNamespace(), wrapperName);
                    propertyStack.addLast(new ArrayFrame(keyFrame.key(), null, keyFrame.namespace(), key.cdata(), key.nullHandling(), false, false));
                    yield true;
                }
                case DEFAULT -> false;
            };
            case null -> false;
        };
    }

    private void encodeRootArray(Argument<?> type) throws XMLStreamException {
        String collectionName = rootName;
        if (collectionName == null) {
            Class<?> javaType = type.getType();
            String typeName = javaType.isArray()
                ? javaType.getComponentType().getSimpleName() + "s"
                : type.getName();
            collectionName = NameUtils.camelCase(typeName, false);
        }
        propertyStack.addLast(new ArrayFrame(collectionName, "item", null, false, XmlNullHandling.DEFAULT, false, false));
        xmlWriter.writeStartElement(collectionName);
    }

    private @Nullable XmlGenerator rootGenerator(Argument<?> type) {
        if (!propertyStack.isEmpty()) {
            return null;
        }
        var annotationMetadata = type.getAnnotationMetadata();
        String rootNamespace = annotationMetadata
            .stringValue(SerdeConfig.class, SerdeConfig.XML_NAMESPACE)
            .orElse(null);
        if (annotationMetadata.stringValue(SerdeConfig.class, SerdeConfig.WRAPPER_PROPERTY).isPresent()) {
            return new XmlGenerator(xmlWriter, Boolean.TRUE, rootNamespace);
        }
        if (rootNamespace != null
            && annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.XML_ROOT_ELEMENT).orElse(false)) {
            return new XmlGenerator(xmlWriter, Boolean.TRUE, rootNamespace);
        }
        return null;
    }

    private Encoder encodeObjectFrame(String name) throws XMLStreamException {
        if (rootMapper) {
            rootMapper = false;
            return this;
        }
        ContextProperties lastFrame = propertyStack.peekLast();
        if (lastFrame instanceof KeyFrame keyFrame) {
            return encodeKeyObject(keyFrame);
        }
        if (lastFrame instanceof ArrayFrame arrayFrame) {
            return encodeArrayObject(arrayFrame, name);
        }
        propertyStack.addLast(new ObjectFrame(name));
        xmlWriter.writeStartElement(name);
        return this;
    }

    private Encoder encodeKeyObject(KeyFrame keyFrame) {
        Deque<ContextProperties> innerPropertyStack = new ArrayDeque<>(8);
        KeyFrame childFrame = new KeyFrame(
            keyFrame.key(),
            false,
            keyFrame.arrayWrappingKey(),
            Boolean.FALSE,
            keyFrame.namespace(),
            keyFrame.xmlKey()
        );
        propertyStack.removeLast();
        propertyStack.addLast(new KeyFrame(
            keyFrame.key(),
            true,
            keyFrame.arrayWrappingKey(),
            Boolean.TRUE,
            keyFrame.namespace(),
            keyFrame.xmlKey()
        ));
        innerPropertyStack.addLast(childFrame);
        innerPropertyStack.addLast(new ObjectFrame(null));
        return new XmlGenerator(xmlWriter, innerPropertyStack);
    }

    private Encoder encodeArrayObject(ArrayFrame arrayFrame, String fallbackName) throws XMLStreamException {
        String itemName = arrayItemName(arrayFrame, fallbackName);
        Deque<ContextProperties> innerPropertyStack = new ArrayDeque<>(8);
        innerPropertyStack.addLast(new ObjectFrame(itemName));
        writeStartElement(arrayFrame.itemNamespace(), itemName);
        return new XmlGenerator(xmlWriter, innerPropertyStack);
    }

    private static String arrayItemName(ArrayFrame arrayFrame, String fallbackName) {
        String iterableKey = arrayFrame.iterableKey();
        if (iterableKey != null && !iterableKey.isEmpty()) {
            return iterableKey;
        }
        String arrayKey = arrayFrame.key();
        return arrayKey == null || arrayKey.isEmpty() ? fallbackName : arrayKey;
    }

    private void finishKeyFrame() throws XMLStreamException {
        xmlWriter.writeEndElement();
        propertyStack.clear();
    }

    private void finishObjectFrame(ObjectFrame objectFrame) throws XMLStreamException {
        if (objectFrame.key() != null) {
            xmlWriter.writeEndElement();
        } else if (propertyStack.peekFirst() instanceof KeyFrame keyFrame) {
            if (Boolean.TRUE.equals(keyFrame.objectWrappingKey())) {
                xmlWriter.writeEndElement();
            } else {
                writeEmptyElement(keyFrame.namespace(), keyFrame.key());
            }
        }
        propertyStack.clear();
    }

    private void finishArrayFrame(ArrayFrame arrayFrame) throws XMLStreamException {
        if (arrayFrame.key() != null && arrayFrame.key().isEmpty()) {
            propertyStack.removeLast();
            return;
        }
        if (propertyStack.size() == 1) {
            xmlWriter.writeEndElement();
            return;
        }
        propertyStack.removeLast();
        xmlWriter.writeEndElement();
        assert propertyStack.peekLast() instanceof KeyFrame : "Expected KeyFrame, got: " + propertyStack.peekLast();
        KeyFrame last = (KeyFrame) propertyStack.removeLast();
        propertyStack.addLast(new KeyFrame(
            last.key(),
            true,
            last.arrayWrappingKey(),
            last.objectWrappingKey(),
            last.namespace(),
            last.xmlKey()
        ));
    }

    private void writeKeyScalar(KeyFrame keyFrame, String data) throws XMLStreamException {
        XmlKey xmlKey = keyFrame.xmlKey();
        switch (xmlKey) {
            case XmlKey key when key.attribute() -> writeAttribute(keyFrame, data);
            case XmlKey key when key.text() -> writeText(data, key.cdata());
            case XmlKey key -> {
                writeStartElement(keyFrame.namespace(), keyFrame.key());
                writeText(data, key.cdata());
                xmlWriter.writeEndElement();
            }
            case null -> {
                writeStartElement(keyFrame.namespace(), keyFrame.key());
                writeText(data, false);
                xmlWriter.writeEndElement();
            }
        }
        markConsumed(keyFrame);
    }

    private void writeAttribute(KeyFrame keyFrame, String data) throws XMLStreamException {
        String key = Objects.requireNonNull(keyFrame.key());
        if (keyFrame.namespace() == null || keyFrame.namespace().isEmpty()) {
            xmlWriter.writeAttribute(key, data);
        } else {
            xmlWriter.writeAttribute(keyFrame.namespace(), key, data);
        }
    }

    private void writeArrayScalar(ArrayFrame arrayFrame, String data) throws XMLStreamException {
        if (arrayFrame.textList()) {
            if (arrayFrame.itemWritten()) {
                xmlWriter.writeCharacters(" ");
            }
            writeText(data, arrayFrame.cdata());
            propertyStack.removeLast();
            propertyStack.addLast(arrayFrame.withItemWritten());
            return;
        }
        String iterableKey = arrayFrame.iterableKey();
        String itemName = iterableKey != null && !iterableKey.isEmpty() ? iterableKey : arrayFrame.key();
        writeStartElement(arrayFrame.itemNamespace(), itemName);
        writeText(data, arrayFrame.cdata());
        xmlWriter.writeEndElement();
    }

    private void writeRootScalar(String data) throws XMLStreamException {
        xmlWriter.writeStartElement(rootName == null ? "value" : rootName);
        xmlWriter.writeCharacters(data);
        xmlWriter.writeEndElement();
    }

    private void markConsumed(KeyFrame keyFrame) {
        propertyStack.removeLast();
        propertyStack.addLast(new KeyFrame(
            keyFrame.key(),
            true,
            keyFrame.arrayWrappingKey(),
            keyFrame.objectWrappingKey(),
            keyFrame.namespace(),
            keyFrame.xmlKey()
        ));
    }

    @Override
    public void encodeString(String value) throws IOException {
        writeScalar(value);
    }

    @Override
    public void encodeBoolean(boolean value) throws IOException {
        writeScalar(Boolean.toString(value));
    }

    @Override
    public void encodeByte(byte value) throws IOException {
        writeScalar(Byte.toString(value));
    }

    @Override
    public void encodeBinary(byte [] data) throws IOException {
        writeScalar(Base64.getEncoder().encodeToString(data));
    }

    @Override
    public void encodeShort(short value) throws IOException {
        writeScalar(Short.toString(value));
    }

    @Override
    public void encodeChar(char value) throws IOException {
        writeScalar(Character.toString(value));
    }

    @Override
    public void encodeInt(int value) throws IOException {
        writeScalar(Integer.toString(value));
    }

    @Override
    public void encodeLong(long value) throws IOException {
        writeScalar(Long.toString(value));
    }

    @Override
    public void encodeFloat(float value) throws IOException {
        writeScalar(Float.toString(value));
    }

    @Override
    public void encodeDouble(double value) throws IOException {
        writeScalar(Double.toString(value));
    }

    @Override
    public void encodeBigInteger(BigInteger value) throws IOException {
        writeScalar(String.valueOf(value));
    }

    @Override
    public void encodeBigDecimal(BigDecimal value) throws IOException {
        writeScalar(String.valueOf(value));
    }

    @Override
    public void encodeNull() throws IOException {
        try {
            ContextProperties lastProperty = propertyStack.peekLast();
            switch (lastProperty) {
                case KeyFrame kf -> {
                    XmlKey xmlKey = kf.xmlKey();
                    switch (xmlKey) {
                        case XmlKey key when key.attribute() || key.text() ->
                            skipNull(); // XML attributes and text content cannot represent null values.
                        case XmlKey key when key.collectionLayout() == XmlCollectionLayout.WRAPPED
                            || key.wrapperNullHandling() != XmlNullHandling.DEFAULT -> {
                            String wrapperName = key.wrapperName() == null ? kf.key() : key.wrapperName();
                            switch (key.wrapperNullHandling()) {
                                case NIL -> writeNilElement(key.wrapperNamespace(), wrapperName);
                                case OMIT -> {
                                    skipNull(); // A non-nillable wrapper represents a null collection by absence.
                                }
                                case DEFAULT -> writeEmptyElement(kf.namespace(), kf.key());
                                default -> throw new IllegalStateException("Unexpected wrapper null handling");
                            }
                        }
                        case XmlKey key -> {
                            switch (key.nullHandling()) {
                                case NIL -> writeNilElement(kf.namespace(), kf.key());
                                case OMIT -> {
                                    skipNull(); // A non-nillable element represents null by absence.
                                }
                                case DEFAULT -> writeEmptyElement(kf.namespace(), kf.key());
                                default -> throw new IllegalStateException("Unexpected element null handling");
                            }
                        }
                        case null -> writeEmptyElement(kf.namespace(), kf.key());
                    }
                    propertyStack.removeLast();
                    propertyStack.addLast(new KeyFrame(kf.key(), true, kf.arrayWrappingKey(), kf.objectWrappingKey(), kf.namespace(), xmlKey));
                }
                case ArrayFrame af -> {
                    String iterableKey = af.iterableKey();
                    String itemName = (iterableKey != null && !iterableKey.isEmpty()) ? iterableKey : af.key();
                    switch (af.itemNullHandling()) {
                        case NIL -> writeNilElement(af.itemNamespace(), itemName);
                        case OMIT -> {
                            skipNull(); // A non-nillable collection item represents null by absence.
                        }
                        case DEFAULT -> writeEmptyElement(af.itemNamespace(), itemName);
                        default -> throw new IllegalStateException("Unexpected item null handling");
                    }
                }
                case null -> xmlWriter.writeEmptyElement(rootName == null ? "null" : rootName);
                default -> xmlWriter.writeEndElement();
            }
        } catch (XMLStreamException e) {
            throw new SerdeException("Error writing XML", e);
        }
    }

    private static void skipNull() {
        // The current XML frame remains open and no XML event is needed.
    }

    private void writeStartElement(@Nullable String namespaceUri, @Nullable String localName) throws XMLStreamException {
        if (namespaceUri == null || namespaceUri.isEmpty()) {
            if (rootNamespace == null) {
                xmlWriter.writeStartElement(localName);
            } else {
                xmlWriter.writeStartElement("", localName, "");
            }
        } else {
            xmlWriter.writeStartElement(namespaceUri, localName);
        }
    }

    private void writeText(String data, boolean cdata) throws XMLStreamException {
        if (cdata) {
            xmlWriter.writeCData(data);
        } else {
            xmlWriter.writeCharacters(data);
        }
    }

    private void writeEmptyElement(@Nullable String namespaceUri, @Nullable String localName) throws XMLStreamException {
        if (namespaceUri == null || namespaceUri.isEmpty()) {
            if (rootNamespace == null) {
                xmlWriter.writeEmptyElement(localName);
            } else {
                xmlWriter.writeEmptyElement("", localName, "");
            }
        } else {
            xmlWriter.writeEmptyElement(namespaceUri, localName);
        }
    }

    private void writeNilElement(@Nullable String namespaceUri, @Nullable String localName) throws XMLStreamException {
        writeStartElement(namespaceUri, localName);
        xmlWriter.writeNamespace("xsi", XSI_NS);
        xmlWriter.writeAttribute("xsi", XSI_NS, "nil", "true");
        xmlWriter.writeEndElement();
    }

    sealed interface ContextProperties permits ObjectFrame, KeyFrame, ArrayFrame {
        @Nullable String key();
    }

    private record ObjectFrame(@Nullable String key) implements ContextProperties {
    }

    private record KeyFrame(
        @Nullable String key,
        boolean consumed,
        @Nullable String arrayWrappingKey,
        @Nullable Boolean objectWrappingKey,
        @Nullable String namespace,
        @Nullable XmlKey xmlKey
    ) implements ContextProperties {
    }

    private record ArrayFrame(
        @Nullable String key,
        @Nullable String iterableKey,
        @Nullable String itemNamespace,
        boolean cdata,
        XmlNullHandling itemNullHandling,
        boolean textList,
        boolean itemWritten
    ) implements ContextProperties {
        private ArrayFrame withItemWritten() {
            return new ArrayFrame(key, iterableKey, itemNamespace, cdata, itemNullHandling, textList, true);
        }
    }
}
