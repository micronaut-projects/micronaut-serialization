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
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;
import org.jspecify.annotations.NonNull;
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
 * @since 3.1.0
 */
@Internal
public final class XmlGenerator implements Encoder {

    private final XMLStreamWriter xmlWriter;
    private final Deque<ContextProperties> propertyStack = new ArrayDeque<>();
    private Boolean rootMapper;
    private @Nullable String pendingRootNamespace;

    /**
     * Creates an XML encoder backed by the supplied StAX writer.
     *
     * <p>The writer is owned by the caller, typically {@link XmlObjectMapper}.</p>
     *
     * @param xmlWriter The XML stream writer to receive encoded events
     */
    public XmlGenerator(XMLStreamWriter xmlWriter) {
        this.xmlWriter = xmlWriter;
        this.rootMapper = false;
    }

    private XmlGenerator(XMLStreamWriter xmlWriter, Boolean rootMapper) {
        this(xmlWriter, rootMapper, null);
    }

    private XmlGenerator(XMLStreamWriter xmlWriter, Boolean rootMapper, @Nullable String rootNamespace) {
        this.xmlWriter = xmlWriter;
        this.rootMapper = rootMapper;
        this.pendingRootNamespace = rootNamespace;
    }

    private XmlGenerator(XMLStreamWriter xmlWriter, Deque<ContextProperties> propertyStack) {
        this.xmlWriter = xmlWriter;
        this.propertyStack.addAll(propertyStack);
        this.rootMapper = false;
    }

    @Override
    public @NonNull Encoder encodeArray(@NonNull Argument<?> type) throws IOException {
        try {
            if (!propertyStack.isEmpty()) {
                ContextProperties lastPropertyKey = propertyStack.peekLast();
                String lastProperty = lastPropertyKey.key();
                if (lastPropertyKey instanceof KeyFrame kf && kf.arrayWrappingKey() != null) {
                    lastProperty = kf.arrayWrappingKey();
                    xmlWriter.writeStartElement(lastProperty);
                    propertyStack.addLast(new ArrayFrame(kf.key(), null));
                    return this;
                }
                xmlWriter.writeStartElement(lastProperty);
                propertyStack.addLast(new ArrayFrame(lastProperty, null));
                return this;
            } else  {
                String collectionName = NameUtils.camelCase(type.getName(), false);
                ArrayFrame arrayFrame = new ArrayFrame(collectionName, "item");
                propertyStack.addLast(arrayFrame);

                xmlWriter.writeStartElement(collectionName);
            }
        } catch (XMLStreamException e) {
            throw new SerdeException("Error writing XML", e);
        }
        return this;
    }

    @Override
    public @NonNull Encoder encodeObject(@NonNull Argument<?> type) throws IOException {
        String name = type.getSimpleName();
        if (type.equals(Argument.OBJECT_ARGUMENT)) {
            Boolean rootMapper = true;
            return new XmlGenerator(xmlWriter, rootMapper);
        }
        if (propertyStack.isEmpty() && type.getAnnotationMetadata().stringValue(SerdeConfig.class, SerdeConfig.WRAPPER_PROPERTY).isPresent()) {
            String rootNamespace = type.getAnnotationMetadata()
                .stringValue(SerdeConfig.class, SerdeConfig.XML_NAMESPACE)
                .orElse(null);
            return new XmlGenerator(xmlWriter, Boolean.TRUE, rootNamespace);
        }
        try {
            if (rootMapper) {
                rootMapper = false;
                return this;
            }

            ContextProperties last = propertyStack.peekLast();
            if (last instanceof KeyFrame || last instanceof ArrayFrame) {
                Deque<ContextProperties> innerPropertyStack = new ArrayDeque<>(8);
                if (last instanceof KeyFrame kf) {
                    KeyFrame updated = new KeyFrame(kf.key(), kf.consumed(), kf.arrayWrappingKey(), Boolean.FALSE);
                    propertyStack.removeLast();
                    propertyStack.addLast(updated);
                    innerPropertyStack.addLast(updated);
                    innerPropertyStack.addLast(new ObjectFrame(null, null));
                    return new XmlGenerator(xmlWriter, innerPropertyStack);
                }
                if (last instanceof ArrayFrame af) {
                    String iterableKey = af.iterableKey();
                    String afKey = af.key();
                    String itemName = (iterableKey != null && !iterableKey.isEmpty()) ? iterableKey
                        : (afKey != null && !afKey.isEmpty()) ? afKey
                        : name;
                    innerPropertyStack.addLast(new ObjectFrame(itemName, null));
                    xmlWriter.writeStartElement(itemName);
                }

                return new XmlGenerator(xmlWriter, innerPropertyStack);
            }

            propertyStack.addLast(new ObjectFrame(name, null));
            xmlWriter.writeStartElement(name);

        } catch (XMLStreamException e) {
            throw new SerdeException("Error writing XML", e);
        }
        return this;
    }

    @Override
    public void finishStructure() throws IOException {
        try {
            ContextProperties lastProperty = propertyStack.peekLast();
            switch (lastProperty) {
                case KeyFrame kf -> {
                    xmlWriter.writeEndElement();
                    propertyStack.clear();
                }
                case ObjectFrame of -> {
                    if (of.key() != null) {
                        xmlWriter.writeEndElement();
                    }
                    if (propertyStack.peekFirst() instanceof KeyFrame kf) {
                        if (of.key() == null) {
                            if (Boolean.TRUE.equals(kf.objectWrappingKey())) {
                                xmlWriter.writeEndElement();
                            } else {
                                xmlWriter.writeEmptyElement(kf.key());
                            }
                        }
                    }
                    propertyStack.clear();
                }
                case ArrayFrame af -> {
                    if (af.key() != null && af.key().isEmpty()) {
                        propertyStack.removeLast();
                        return;
                    }
                    if (propertyStack.size() == 1 && propertyStack.peekLast() instanceof ArrayFrame) {
                        xmlWriter.writeEndElement();
                        return;
                    }
                    propertyStack.removeLast();
                    xmlWriter.writeEndElement();
                    assert propertyStack.peekLast() instanceof KeyFrame : "Expected KeyFrame, got: " + propertyStack.peekLast();
                    KeyFrame last = (KeyFrame) propertyStack.removeLast();
                    propertyStack.addLast(new KeyFrame(last.key(), true, last.arrayWrappingKey(), last.objectWrappingKey()));

                } case null -> {
                    assert  propertyStack.isEmpty() : "Root name mapping";
                }
                default -> throw new IllegalStateException("Unexpected value: " + lastProperty);
            }

        } catch (XMLStreamException e) {
            throw new SerdeException("Error writing XML", e);
        }
    }

    @Override
    public void encodeKey(@NonNull String key) throws IOException {
        try {
            if (rootMapper) {
                propertyStack.addLast(new ObjectFrame(key, Boolean.TRUE));
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
                propertyStack.addLast(new KeyFrame(of.key(), of.consumed(), key, of.objectWrappingKey()));
                return;
            }

            propertyStack.addLast(new KeyFrame(key, false, null, null));

        } catch (XMLStreamException e) {
            throw new SerdeException("Error writing XML", e);
        }
    }

    private void ensurePendingObjectElementStarted() throws XMLStreamException {
        if (propertyStack.size() == 2
            && propertyStack.peekFirst() instanceof KeyFrame keyFrame
            && propertyStack.peekLast() instanceof ObjectFrame objectFrame
            && objectFrame.key() == null
            && !Boolean.TRUE.equals(keyFrame.objectWrappingKey())) {
            xmlWriter.writeStartElement(keyFrame.key());
            propertyStack.removeFirst();
            propertyStack.addFirst(new KeyFrame(keyFrame.key(), keyFrame.consumed(), keyFrame.arrayWrappingKey(), Boolean.TRUE));
        }
    }

    private void writeScalar(String data) throws IOException {
        try {
            ContextProperties lastProperty = propertyStack.getLast();
            switch (lastProperty) {
                case KeyFrame kf -> {
                    xmlWriter.writeStartElement(kf.key());
                    xmlWriter.writeCharacters(data);
                    xmlWriter.writeEndElement();
                    propertyStack.removeLast();
                    propertyStack.addLast(new KeyFrame(kf.key(), true, kf.arrayWrappingKey(), kf.objectWrappingKey()));
                }
                case ArrayFrame af -> {
                    String iterableKey = af.iterableKey();
                    String itemName = (iterableKey != null && !iterableKey.isEmpty()) ? iterableKey : af.key();
                    xmlWriter.writeStartElement(itemName);
                    xmlWriter.writeCharacters(data);
                    xmlWriter.writeEndElement();
                }
                default -> throw new IllegalStateException("Unexpected value in writeScalar(): " + lastProperty + "\t " + lastProperty.getClass().getName());
            }
        } catch (XMLStreamException e) {
            throw new SerdeException("Error writing XML", e);
        }
    }

    /**
     * Encodes an array inline, reusing the pending key as the element name and writing
     * no wrapper element around the items.
     *
     * @param type the array type
     * @return this encoder
     * @throws IOException if encoding fails
     */
    public @NonNull Encoder encodeInlineArray(@NonNull Argument<?> type) throws IOException {
        ContextProperties lastProperty = propertyStack.peekLast();
        if (!(lastProperty instanceof KeyFrame keyFrame) || keyFrame.consumed()) {
            throw new IllegalStateException("Expected a pending key before starting an inline array, but found: " + lastProperty);
        }

        propertyStack.removeLast();
        propertyStack.addLast(new ArrayFrame("", keyFrame.key()));
        return this;
    }

    @Override
    public void encodeString(@NonNull String value) throws IOException {
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
    public void encodeBinary(byte @NonNull [] data) throws IOException {
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
    public void encodeBigInteger(@NonNull BigInteger value) throws IOException {
        writeScalar(String.valueOf(value));
    }

    @Override
    public void encodeBigDecimal(@NonNull BigDecimal value) throws IOException {
        writeScalar(String.valueOf(value));
    }

    @Override
    public void encodeNull() throws IOException {
        try {
            ContextProperties lastProperty = propertyStack.peekLast();
            switch (lastProperty) {
                case KeyFrame kf -> {
                    xmlWriter.writeEmptyElement(kf.key());
                    propertyStack.removeLast();
                    propertyStack.addLast(new KeyFrame(kf.key(), true, kf.arrayWrappingKey(), kf.objectWrappingKey()));
                }
                case null -> xmlWriter.writeEndElement();
                default -> xmlWriter.writeEndElement();
            }
        } catch (XMLStreamException e) {
            throw new SerdeException("Error writing XML", e);
        }
    }

    /**
     * Gets the XML stream writer for custom SerDes.
     *
     * @return The XML stream writer.
     */
    public XMLStreamWriter getXmlWriter() {
        return xmlWriter;
    }

    /**
     * Writes an XML attribute using the current pending property key as the attribute name.
     *
     * @param value the attribute value
     * @throws IOException if writing fails
     */
    public void writeAttributeForCurrentKey(String value) throws IOException {
        ContextProperties lastProperty = propertyStack.peekLast();
        if (!(lastProperty instanceof KeyFrame keyFrame)) {
            throw new IllegalStateException("Expected a pending XML key before writing an attribute, but found: " + lastProperty);
        }
        writeAttributeForCurrentKey(Objects.requireNonNull(keyFrame.key()), value);
    }

    /**
     * Writes an XML attribute using the supplied local name.
     *
     * @param localName the attribute local name
     * @param value the attribute value
     * @throws IOException if writing fails
     */
    public void writeAttributeForCurrentKey(String localName, String value) throws IOException {
        ContextProperties lastProperty = propertyStack.peekLast();
        if (!(lastProperty instanceof KeyFrame keyFrame)) {
            throw new IllegalStateException("Expected a pending XML key before writing an attribute, but found: " + lastProperty);
        }
        try {
            xmlWriter.writeAttribute(localName, value);
            propertyStack.removeLast();
            propertyStack.addLast(new KeyFrame(keyFrame.key(), true, keyFrame.arrayWrappingKey(), keyFrame.objectWrappingKey()));
        } catch (XMLStreamException e) {
            throw new SerdeException("Error writing XML", e);
        }
    }

    /**
     * Write a namespaced XML attribute for the current pending property key.
     * The local name is the pending key on the stack; the namespace URI is supplied here.
     *
     * @param namespaceUri the attribute namespace URI
     * @param value the textual attribute value
     */
    public void writeNamespacedAttributeForCurrentKey(String namespaceUri, String value) throws IOException {
        ContextProperties lastProperty = propertyStack.peekLast();
        if (!(lastProperty instanceof KeyFrame keyFrame)) {
            throw new IllegalStateException("Expected a pending XML key before writing an attribute, but found: " + lastProperty);
        }
        writeNamespacedAttributeForCurrentKey(namespaceUri, Objects.requireNonNull(keyFrame.key()), value);
    }

    /**
     * Write a namespaced XML attribute for the current pending property key.
     *
     * @param namespaceUri the attribute namespace URI
     * @param localName the attribute local name
     * @param value the textual attribute value
     */
    public void writeNamespacedAttributeForCurrentKey(String namespaceUri, String localName, String value) throws IOException {
        ContextProperties lastProperty = propertyStack.peekLast();
        if (!(lastProperty instanceof KeyFrame keyFrame)) {
            throw new IllegalStateException("Expected a pending XML key before writing an attribute, but found: " + lastProperty);
        }
        try {
            xmlWriter.writeAttribute(namespaceUri, localName, value);
            propertyStack.removeLast();
            propertyStack.addLast(new KeyFrame(keyFrame.key(), true, keyFrame.arrayWrappingKey(), keyFrame.objectWrappingKey()));
        } catch (XMLStreamException e) {
            throw new SerdeException("Error writing XML", e);
        }
    }

    /**
     * Writes a namespaced scalar element for the current pending property key.
     *
     * @param localName the element local name
     * @param namespaceUri the namespace URI (may be null/empty for the default namespace)
     * @param value the textual value
     */
    public void writeNamespacedScalarForCurrentKey(String localName, @Nullable String namespaceUri, String value) throws IOException {
        try {
            ensurePendingObjectElementStarted();
            ContextProperties last = propertyStack.peekLast();
            if (!(last instanceof KeyFrame keyFrame)) {
                throw new IllegalStateException("Expected a pending XML key before writing a namespaced element, but found: " + last);
            }
            if (namespaceUri == null || namespaceUri.isEmpty()) {
                xmlWriter.writeStartElement(localName);
            } else {
                xmlWriter.writeStartElement(namespaceUri, localName);
            }
            xmlWriter.writeCharacters(value);
            xmlWriter.writeEndElement();
            propertyStack.removeLast();
            propertyStack.addLast(new KeyFrame(keyFrame.key(), true, keyFrame.arrayWrappingKey(), keyFrame.objectWrappingKey()));
        } catch (XMLStreamException e) {
            throw new SerdeException("Error writing XML", e);
        }
    }

    /**
     * Writes the start element for the current pending key. Closing the element is the
     * responsibility of the custom wrapper serializer.
     */
    public void wrapElement() throws IOException {
        ContextProperties lastKey = propertyStack.peekLast();
        try {
            xmlWriter.writeStartElement(lastKey.key());
        } catch (XMLStreamException e) {
            throw new SerdeException("Error writing XML", e);
        }
    }

    sealed interface ContextProperties permits ObjectFrame, KeyFrame, ArrayFrame {
        @Nullable String key();
    }

    private record ObjectFrame(@Nullable String key, @Nullable Boolean rootName) implements ContextProperties {
    }

    private record KeyFrame(
        @Nullable String key,
        boolean consumed,
        @Nullable String arrayWrappingKey,
        @Nullable Boolean objectWrappingKey
    ) implements ContextProperties {
    }

    private record ArrayFrame(@Nullable String key, @Nullable String iterableKey) implements ContextProperties {
    }
}
