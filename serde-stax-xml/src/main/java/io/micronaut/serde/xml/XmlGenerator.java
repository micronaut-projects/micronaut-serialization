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
public final class XmlGenerator implements KeysAwareEncoder {

    private static final int XML_KEYS_CONTRIBUTION_INDEX = KeysSupport.indexOf(new XmlKeysProvider());

    private final XMLStreamWriter xmlWriter;
    private final Deque<ContextProperties> propertyStack = new ArrayDeque<>();
    private boolean rootMapper;
    private final @Nullable String rootName;
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
        this.rootMapper = false;
    }

    private XmlGenerator(XMLStreamWriter xmlWriter, Boolean rootMapper) {
        this(xmlWriter, rootMapper, null);
    }

    private XmlGenerator(XMLStreamWriter xmlWriter, Boolean rootMapper, @Nullable String rootNamespace) {
        this.xmlWriter = xmlWriter;
        this.rootName = null;
        this.rootMapper = rootMapper;
        this.pendingRootNamespace = rootNamespace;
    }

    private XmlGenerator(XMLStreamWriter xmlWriter, Deque<ContextProperties> propertyStack) {
        this.xmlWriter = xmlWriter;
        this.rootName = null;
        this.propertyStack.addAll(propertyStack);
        this.rootMapper = false;
    }

    @Override
    public Encoder encodeArray(Argument<?> type) throws IOException {
        try {
            if (!propertyStack.isEmpty()) {
                ContextProperties lastPropertyKey = propertyStack.peekLast();
                String lastProperty = lastPropertyKey.key();
                if (lastPropertyKey instanceof KeyFrame kf
                    && kf.xmlKey() != null
                    && kf.xmlKey().attribute()) {
                    throw new SerdeException("XML attributes cannot contain array values: " + kf.key());
                }
                if (lastPropertyKey instanceof KeyFrame kf && kf.arrayWrappingKey() != null) {
                    lastProperty = kf.arrayWrappingKey();
                    xmlWriter.writeStartElement(lastProperty);
                    propertyStack.addLast(new ArrayFrame(kf.key(), null, kf.namespace(), false));
                    return this;
                }
                if (lastPropertyKey instanceof KeyFrame kf && kf.xmlKey() != null) {
                    if (kf.xmlKey().collectionLayout() == XmlCollectionLayout.INLINE) {
                        propertyStack.removeLast();
                        propertyStack.addLast(new ArrayFrame("", kf.key(), kf.namespace(), kf.xmlKey().cdata()));
                        return this;
                    }
                    if (kf.xmlKey().collectionLayout() == XmlCollectionLayout.WRAPPED) {
                        String wrapperName = kf.xmlKey().wrapperName() == null
                            ? Objects.requireNonNull(kf.key())
                            : kf.xmlKey().wrapperName();
                        writeStartElement(kf.xmlKey().wrapperNamespace(), wrapperName);
                        propertyStack.addLast(new ArrayFrame(kf.key(), null, kf.namespace(), kf.xmlKey().cdata()));
                        return this;
                    }
                }
                xmlWriter.writeStartElement(lastProperty);
                String itemNamespace = lastPropertyKey instanceof KeyFrame keyFrame
                    ? keyFrame.namespace()
                    : ((ArrayFrame) lastPropertyKey).itemNamespace();
                boolean cdata = lastPropertyKey instanceof KeyFrame keyFrame
                    && keyFrame.xmlKey() != null
                    && keyFrame.xmlKey().cdata();
                propertyStack.addLast(new ArrayFrame(lastProperty, null, itemNamespace, cdata));
                return this;
            } else {
                String collectionName = rootName;
                if (collectionName == null) {
                    Class<?> javaType = type.getType();
                    String typeName = javaType.isArray()
                        ? javaType.getComponentType().getSimpleName() + "s"
                        : type.getName();
                    collectionName = NameUtils.camelCase(typeName, false);
                }
                ArrayFrame arrayFrame = new ArrayFrame(collectionName, "item", null, false);
                propertyStack.addLast(arrayFrame);

                xmlWriter.writeStartElement(collectionName);
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
        if (propertyStack.isEmpty()) {
            String rootNamespace = type.getAnnotationMetadata()
                .stringValue(SerdeConfig.class, SerdeConfig.XML_NAMESPACE)
                .orElse(null);
            if (type.getAnnotationMetadata().stringValue(SerdeConfig.class, SerdeConfig.WRAPPER_PROPERTY).isPresent()
                || (rootNamespace != null
                    && type.getAnnotationMetadata().booleanValue(SerdeConfig.class, SerdeConfig.XML_ROOT_ELEMENT).orElse(false))) {
                return new XmlGenerator(xmlWriter, Boolean.TRUE, rootNamespace);
            }
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
                    KeyFrame childFrame = new KeyFrame(kf.key(), false, kf.arrayWrappingKey(), Boolean.FALSE, kf.namespace(), kf.xmlKey());
                    propertyStack.removeLast();
                    propertyStack.addLast(new KeyFrame(kf.key(), true, kf.arrayWrappingKey(), Boolean.TRUE, kf.namespace(), kf.xmlKey()));
                    innerPropertyStack.addLast(childFrame);
                    innerPropertyStack.addLast(new ObjectFrame(null));
                    return new XmlGenerator(xmlWriter, innerPropertyStack);
                }
                if (last instanceof ArrayFrame af) {
                    String iterableKey = af.iterableKey();
                    String afKey = af.key();
                    String itemName = (iterableKey != null && !iterableKey.isEmpty()) ? iterableKey
                        : (afKey != null && !afKey.isEmpty()) ? afKey
                        : name;
                    innerPropertyStack.addLast(new ObjectFrame(itemName));
                    writeStartElement(af.itemNamespace(), itemName);
                }

                return new XmlGenerator(xmlWriter, innerPropertyStack);
            }

            propertyStack.addLast(new ObjectFrame(name));
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
                                writeEmptyElement(kf.namespace(), kf.key());
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
                    propertyStack.addLast(new KeyFrame(last.key(), true, last.arrayWrappingKey(), last.objectWrappingKey(), last.namespace(), last.xmlKey()));

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
                case KeyFrame kf -> {
                    if (kf.xmlKey() != null && kf.xmlKey().attribute()) {
                        if (kf.namespace() == null || kf.namespace().isEmpty()) {
                            xmlWriter.writeAttribute(Objects.requireNonNull(kf.key()), data);
                        } else {
                            xmlWriter.writeAttribute(kf.namespace(), Objects.requireNonNull(kf.key()), data);
                        }
                        propertyStack.removeLast();
                        propertyStack.addLast(new KeyFrame(kf.key(), true, kf.arrayWrappingKey(), kf.objectWrappingKey(), kf.namespace(), kf.xmlKey()));
                        return;
                    }
                    if (kf.xmlKey() != null && kf.xmlKey().text()) {
                        writeText(data, kf.xmlKey().cdata());
                        propertyStack.removeLast();
                        propertyStack.addLast(new KeyFrame(kf.key(), true, kf.arrayWrappingKey(), kf.objectWrappingKey(), kf.namespace(), kf.xmlKey()));
                        return;
                    }
                    writeStartElement(kf.namespace(), kf.key());
                    writeText(data, kf.xmlKey() != null && kf.xmlKey().cdata());
                    xmlWriter.writeEndElement();
                    propertyStack.removeLast();
                    propertyStack.addLast(new KeyFrame(kf.key(), true, kf.arrayWrappingKey(), kf.objectWrappingKey(), kf.namespace(), kf.xmlKey()));
                }
                case ArrayFrame af -> {
                    String iterableKey = af.iterableKey();
                    String itemName = (iterableKey != null && !iterableKey.isEmpty()) ? iterableKey : af.key();
                    writeStartElement(af.itemNamespace(), itemName);
                    writeText(data, af.cdata());
                    xmlWriter.writeEndElement();
                }
                case null -> {
                    xmlWriter.writeStartElement(rootName == null ? "value" : rootName);
                    xmlWriter.writeCharacters(data);
                    xmlWriter.writeEndElement();
                }
                default -> throw new IllegalStateException("Unexpected value in writeScalar(): " + lastProperty + "\t " + lastProperty.getClass().getName());
            }
        } catch (XMLStreamException e) {
            throw new SerdeException("Error writing XML", e);
        }
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
                    if (kf.xmlKey() == null || !kf.xmlKey().text()) {
                        writeEmptyElement(kf.namespace(), kf.key());
                    }
                    propertyStack.removeLast();
                    propertyStack.addLast(new KeyFrame(kf.key(), true, kf.arrayWrappingKey(), kf.objectWrappingKey(), kf.namespace(), kf.xmlKey()));
                }
                case ArrayFrame af -> {
                    String iterableKey = af.iterableKey();
                    String itemName = (iterableKey != null && !iterableKey.isEmpty()) ? iterableKey : af.key();
                    writeEmptyElement(af.itemNamespace(), itemName);
                }
                case null -> xmlWriter.writeEmptyElement(rootName == null ? "null" : rootName);
                default -> xmlWriter.writeEndElement();
            }
        } catch (XMLStreamException e) {
            throw new SerdeException("Error writing XML", e);
        }
    }

    private void writeStartElement(@Nullable String namespaceUri, @Nullable String localName) throws XMLStreamException {
        if (namespaceUri == null || namespaceUri.isEmpty()) {
            xmlWriter.writeStartElement(localName);
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
            xmlWriter.writeEmptyElement(localName);
        } else {
            xmlWriter.writeEmptyElement(namespaceUri, localName);
        }
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
        boolean cdata
    ) implements ContextProperties {
    }
}
