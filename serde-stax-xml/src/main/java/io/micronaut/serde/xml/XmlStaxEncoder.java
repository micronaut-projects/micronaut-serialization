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
import java.util.Base64;
import java.util.Objects;
import javax.xml.namespace.QName;

/**
 * An {@link Encoder} that serializes objects to XML using a StAX {@link XMLStreamWriter}.
 *
 * <p>Each returned encoder owns exactly one XML scope. Object scopes retain one pending property
 * between {@link #encodeKey(String)} and the following value call; array scopes retain only their
 * item layout. XML content is written directly to the StAX writer.</p>
 *
 * @since 3.2
 */
@Internal
public final class XmlStaxEncoder implements KeysAwareEncoder, XmlEncoder {

    private enum Scope {
        DOCUMENT,
        OBJECT,
        ARRAY
    }

    private static final int XML_KEYS_CONTRIBUTION_INDEX = KeysSupport.indexOf(new XmlKeysProvider());
    private static final String XSI_NS = "http://www.w3.org/2001/XMLSchema-instance";

    private final XMLStreamWriter xmlWriter;
    private final Scope scope;
    private final @Nullable String rootName;
    private final @Nullable String rootNamespace;
    private final @Nullable ObjectScope objectScope;
    private final @Nullable ArrayScope arrayScope;
    private boolean finished;

    /**
     * Creates an XML encoder with a fallback document root for scalar values.
     *
     * @param xmlWriter The XML stream writer to receive encoded events
     * @param rootName The scalar document root, or {@code null} to use {@code value}
     */
    XmlStaxEncoder(XMLStreamWriter xmlWriter, @Nullable String rootName) {
        this(xmlWriter, Scope.DOCUMENT, rootName, null, null, null);
    }

    private XmlStaxEncoder(XMLStreamWriter xmlWriter,
                           Scope scope,
                           @Nullable String rootName,
                           @Nullable String rootNamespace,
                           @Nullable ObjectScope objectScope,
                           @Nullable ArrayScope arrayScope) {
        this.xmlWriter = xmlWriter;
        this.scope = scope;
        this.rootName = rootName;
        this.rootNamespace = rootNamespace;
        this.objectScope = objectScope;
        this.arrayScope = arrayScope;
    }

    private static XmlStaxEncoder objectScope(XMLStreamWriter writer,
                                              @Nullable String rootNamespace,
                                              ObjectScope objectScope) {
        return new XmlStaxEncoder(writer, Scope.OBJECT, null, rootNamespace, objectScope, null);
    }

    private static XmlStaxEncoder arrayScope(XMLStreamWriter writer,
                                             @Nullable String rootNamespace,
                                             ArrayScope arrayScope) {
        return new XmlStaxEncoder(writer, Scope.ARRAY, null, rootNamespace, null, arrayScope);
    }

    @Override
    public Encoder encodeArray(Argument<?> type) throws IOException {
        try {
            return switch (scope) {
                case DOCUMENT -> encodeRootArray(type);
                case OBJECT -> encodeObjectArray();
                case ARRAY -> encodeNestedArray();
            };
        } catch (XMLStreamException e) {
            throw new SerdeException("Error writing XML", e);
        }
    }

    @Override
    public Encoder encodeObject(Argument<?> type) throws IOException {
        try {
            if (type.equals(Argument.OBJECT_ARGUMENT)) {
                clearPendingProperty();
                return objectScope(xmlWriter, rootNamespace, ObjectScope.rootMapping());
            }
            return switch (scope) {
                case DOCUMENT -> encodeRootObject(type);
                case OBJECT -> encodeNestedObject();
                case ARRAY -> encodeArrayObject(type);
            };
        } catch (XMLStreamException e) {
            throw new SerdeException("Error writing XML", e);
        }
    }

    @Override
    public void finishStructure() throws IOException {
        if (finished) {
            return;
        }
        try {
            switch (scope) {
                case DOCUMENT -> {
                }
                case OBJECT -> finishObject();
                case ARRAY -> finishArray();
                default -> throw new IllegalStateException("Unexpected XML scope: " + scope);
            }
            finished = true;
        } catch (XMLStreamException e) {
            throw new SerdeException("Error writing XML", e);
        }
    }

    @Override
    public void encodeKey(String key) throws IOException {
        ObjectScope object = requireObjectScope();
        try {
            if (object.rootMapping && !object.started) {
                object.started = true;
                object.ownsElement = true;
                if (object.pendingRootNamespace != null) {
                    xmlWriter.writeStartElement("", key, object.pendingRootNamespace);
                    object.pendingRootNamespace = null;
                } else {
                    xmlWriter.writeStartElement(key);
                }
                return;
            }
            ensureObjectStarted(object);
            if (object.pendingProperty == null) {
                object.pendingProperty = new PendingProperty(key);
            } else {
                object.pendingProperty.wrappingKey = key;
            }
        } catch (XMLStreamException e) {
            throw new SerdeException("Error writing XML", e);
        }
    }

    @Override
    public void encodeAttributeKey(QName key) throws IOException {
        String namespace = key.getNamespaceURI();
        encodeKey(key.getLocalPart());
        ObjectScope object = requireObjectScope();
        PendingProperty property = object.pendingProperty;
        if (property != null) {
            property.namespace = namespace;
            property.xmlKey = new XmlKey(
                key.getLocalPart(),
                namespace,
                true,
                false,
                false,
                false,
                false,
                XmlCollectionLayout.DEFAULT,
                null,
                null,
                null,
                XmlNullHandling.DEFAULT,
                XmlNullHandling.DEFAULT
            );
        }
    }

    @Override
    public void encodeKey(Keys keys, int index) throws IOException {
        Object[] xmlKeys = KeysSupport.get(keys, XML_KEYS_CONTRIBUTION_INDEX);
        XmlKey xmlKey = ((XmlKey[]) xmlKeys[XmlKeysProvider.XML_KEYS_INDEX])[index];
        encodeKey(xmlKey.name());
        ObjectScope object = requireObjectScope();
        PendingProperty property = object.pendingProperty;
        if (property != null) {
            property.namespace = xmlKey.namespace();
            property.xmlKey = xmlKey;
        }
    }

    private Encoder encodeRootObject(Argument<?> type) throws XMLStreamException {
        var annotationMetadata = type.getAnnotationMetadata();
        @Nullable String namespace = annotationMetadata
            .stringValue(SerdeConfig.class, SerdeConfig.XML_NAMESPACE)
            .orElse(null);
        if (annotationMetadata.stringValue(SerdeConfig.class, SerdeConfig.WRAPPER_PROPERTY).isPresent()
            || (namespace != null
            && annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.XML_ROOT_ELEMENT).orElse(false))) {
            ObjectScope object = ObjectScope.rootMapping();
            object.pendingRootNamespace = namespace;
            return objectScope(xmlWriter, namespace, object);
        }
        String name = type.getSimpleName();
        xmlWriter.writeStartElement(name);
        return objectScope(xmlWriter, null, ObjectScope.started());
    }

    private Encoder encodeNestedObject() throws XMLStreamException {
        ObjectScope object = requireObjectScope();
        if (object.rootMapping && object.started && object.pendingProperty == null) {
            object.rootMapping = false;
            return this;
        }
        PendingProperty property = requirePendingProperty(object);
        object.pendingProperty = null;
        return objectScope(xmlWriter, rootNamespace, ObjectScope.deferred(property));
    }

    private Encoder encodeArrayObject(Argument<?> type) throws XMLStreamException {
        ArrayScope array = requireArrayScope();
        String itemName = array.itemName;
        if (itemName == null || itemName.isEmpty()) {
            itemName = type.getSimpleName();
        }
        writeStartElement(array.itemNamespace, itemName);
        return objectScope(xmlWriter, rootNamespace, ObjectScope.started());
    }

    private Encoder encodeRootArray(Argument<?> type) throws XMLStreamException {
        String collectionName = rootName;
        if (collectionName == null) {
            Class<?> javaType = type.getType();
            String typeName = javaType.isArray()
                ? javaType.getComponentType().getSimpleName() + "s"
                : type.getName();
            collectionName = NameUtils.camelCase(typeName, false);
        }
        xmlWriter.writeStartElement(collectionName);
        return arrayScope(
            xmlWriter,
            null,
            new ArrayScope(true, "item", null, false, XmlNullHandling.DEFAULT, false)
        );
    }

    private Encoder encodeObjectArray() throws IOException, XMLStreamException {
        ObjectScope object = requireObjectScope();
        PendingProperty property = requirePendingProperty(object);
        object.pendingProperty = null;
        XmlKey xmlKey = property.xmlKey;
        if (xmlKey != null && xmlKey.attribute()) {
            throw new SerdeException("XML attributes cannot contain array values: " + property.name);
        }
        if (property.wrappingKey != null) {
            xmlWriter.writeStartElement(property.wrappingKey);
            return arrayScope(
                xmlWriter,
                rootNamespace,
                new ArrayScope(true, property.name, property.namespace, false, XmlNullHandling.DEFAULT, false)
            );
        }
        if (xmlKey == null) {
            xmlWriter.writeStartElement(property.name);
            return arrayScope(
                xmlWriter,
                rootNamespace,
                new ArrayScope(true, property.name, property.namespace, false, XmlNullHandling.DEFAULT, false)
            );
        }
        if (xmlKey.mixed()) {
            return arrayScope(
                xmlWriter,
                rootNamespace,
                new ArrayScope(false, null, property.namespace, xmlKey.cdata(), xmlKey.nullHandling(), true)
            );
        }
        if (xmlKey.list()) {
            writeStartElement(property.namespace, property.name);
            return arrayScope(
                xmlWriter,
                rootNamespace,
                new ArrayScope(true, property.name, property.namespace, xmlKey.cdata(), xmlKey.nullHandling(), true)
            );
        }
        return switch (xmlKey.collectionLayout()) {
            case INLINE -> arrayScope(
                xmlWriter,
                rootNamespace,
                new ArrayScope(false, property.name, property.namespace, xmlKey.cdata(), xmlKey.nullHandling(), false)
            );
            case WRAPPED -> {
                String wrapperName = xmlKey.wrapperName() == null ? property.name : xmlKey.wrapperName();
                writeStartElement(xmlKey.wrapperNamespace(), wrapperName);
                yield arrayScope(
                    xmlWriter,
                    rootNamespace,
                    new ArrayScope(true, property.name, property.namespace, xmlKey.cdata(), xmlKey.nullHandling(), false)
                );
            }
            case DEFAULT -> {
                writeStartElement(property.namespace, property.name);
                yield arrayScope(
                    xmlWriter,
                    rootNamespace,
                    new ArrayScope(true, property.name, property.namespace, xmlKey.cdata(), xmlKey.nullHandling(), false)
                );
            }
        };
    }

    private Encoder encodeNestedArray() throws XMLStreamException {
        ArrayScope array = requireArrayScope();
        String itemName = Objects.requireNonNull(array.itemName, "Nested XML array item name");
        writeStartElement(array.itemNamespace, itemName);
        return arrayScope(
            xmlWriter,
            rootNamespace,
            new ArrayScope(true, itemName, array.itemNamespace, false, XmlNullHandling.DEFAULT, false)
        );
    }

    private void writeScalar(String data) throws IOException {
        try {
            switch (scope) {
                case DOCUMENT -> writeRootScalar(data);
                case OBJECT -> writeObjectScalar(data);
                case ARRAY -> writeArrayScalar(data);
                default -> throw new IllegalStateException("Unexpected XML scope: " + scope);
            }
        } catch (XMLStreamException e) {
            throw new SerdeException("Error writing XML", e);
        }
    }

    private void writeObjectScalar(String data) throws XMLStreamException {
        ObjectScope object = requireObjectScope();
        PendingProperty property = object.pendingProperty;
        if (property == null) {
            if (object.rootMapping && object.started) {
                writeText(data, false);
                return;
            }
            throw new IllegalStateException("No XML property is pending for a scalar value");
        }
        XmlKey xmlKey = property.xmlKey;
        switch (xmlKey) {
            case XmlKey key when key.attribute() -> writeAttribute(property, data);
            case XmlKey key when key.text() -> writeText(data, key.cdata());
            case XmlKey key -> {
                writeStartElement(property.namespace, property.name);
                writeText(data, key.cdata());
                xmlWriter.writeEndElement();
            }
            case null -> {
                writeStartElement(property.namespace, property.name);
                writeText(data, false);
                xmlWriter.writeEndElement();
            }
        }
        object.pendingProperty = null;
    }

    private void writeArrayScalar(String data) throws XMLStreamException {
        ArrayScope array = requireArrayScope();
        if (array.textList) {
            if (array.itemWritten) {
                xmlWriter.writeCharacters(" ");
            }
            writeText(data, array.cdata);
            array.itemWritten = true;
            return;
        }
        writeStartElement(array.itemNamespace, array.itemName);
        writeText(data, array.cdata);
        xmlWriter.writeEndElement();
    }

    private void writeRootScalar(String data) throws XMLStreamException {
        xmlWriter.writeStartElement(rootName == null ? "value" : rootName);
        xmlWriter.writeCharacters(data);
        xmlWriter.writeEndElement();
    }

    private void finishObject() throws XMLStreamException {
        ObjectScope object = requireObjectScope();
        if (object.pendingProperty != null) {
            throw new IllegalStateException("No value encoded for XML property: " + object.pendingProperty.name);
        }
        if (!object.ownsElement) {
            return;
        }
        if (!object.started) {
            PendingProperty owner = Objects.requireNonNull(object.ownerProperty, "ownerProperty");
            writeEmptyElement(owner.namespace, owner.name);
            return;
        }
        xmlWriter.writeEndElement();
    }

    private void finishArray() throws XMLStreamException {
        ArrayScope array = requireArrayScope();
        if (array.ownsElement) {
            xmlWriter.writeEndElement();
        }
    }

    private void ensureObjectStarted(ObjectScope object) throws XMLStreamException {
        if (object.started || object.ownerProperty == null) {
            return;
        }
        writeStartElement(object.ownerProperty.namespace, object.ownerProperty.name);
        object.started = true;
    }

    private void clearPendingProperty() {
        if (objectScope != null) {
            objectScope.pendingProperty = null;
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
            switch (scope) {
                case DOCUMENT -> xmlWriter.writeEmptyElement(rootName == null ? "null" : rootName);
                case OBJECT -> writeObjectNull();
                case ARRAY -> writeArrayNull();
                default -> throw new IllegalStateException("Unexpected XML scope: " + scope);
            }
        } catch (XMLStreamException e) {
            throw new SerdeException("Error writing XML", e);
        }
    }

    private void writeObjectNull() throws XMLStreamException {
        ObjectScope object = requireObjectScope();
        PendingProperty property = requirePendingProperty(object);
        XmlKey xmlKey = property.xmlKey;
        switch (xmlKey) {
            case XmlKey key when key.attribute() || key.text() -> {
            }
            case XmlKey key when key.collectionLayout() == XmlCollectionLayout.WRAPPED
                || key.wrapperNullHandling() != XmlNullHandling.DEFAULT -> {
                String wrapperName = key.wrapperName() == null ? property.name : key.wrapperName();
                switch (key.wrapperNullHandling()) {
                    case NIL -> writeNilElement(key.wrapperNamespace(), wrapperName);
                    case OMIT -> {
                    }
                    case DEFAULT -> writeEmptyElement(property.namespace, property.name);
                    default -> throw new IllegalStateException("Unexpected wrapper null handling");
                }
            }
            case XmlKey key -> {
                switch (key.nullHandling()) {
                    case NIL -> writeNilElement(property.namespace, property.name);
                    case OMIT -> {
                    }
                    case DEFAULT -> writeEmptyElement(property.namespace, property.name);
                    default -> throw new IllegalStateException("Unexpected element null handling");
                }
            }
            case null -> writeEmptyElement(property.namespace, property.name);
        }
        object.pendingProperty = null;
    }

    private void writeArrayNull() throws XMLStreamException {
        ArrayScope array = requireArrayScope();
        switch (array.itemNullHandling) {
            case NIL -> writeNilElement(array.itemNamespace, array.itemName);
            case OMIT -> {
            }
            case DEFAULT -> writeEmptyElement(array.itemNamespace, array.itemName);
            default -> throw new IllegalStateException("Unexpected item null handling");
        }
    }

    private void writeAttribute(PendingProperty property, String data) throws XMLStreamException {
        if (property.namespace == null || property.namespace.isEmpty()) {
            xmlWriter.writeAttribute(property.name, data);
        } else {
            xmlWriter.writeAttribute(property.namespace, property.name, data);
        }
    }

    private void writeStartElement(@Nullable String namespaceUri, @Nullable String localName) throws XMLStreamException {
        String name = Objects.requireNonNull(localName, "XML element name");
        if (namespaceUri == null || namespaceUri.isEmpty()) {
            if (rootNamespace == null) {
                xmlWriter.writeStartElement(name);
            } else {
                xmlWriter.writeStartElement("", name, "");
            }
        } else {
            xmlWriter.writeStartElement(namespaceUri, name);
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
        String name = Objects.requireNonNull(localName, "XML element name");
        if (namespaceUri == null || namespaceUri.isEmpty()) {
            if (rootNamespace == null) {
                xmlWriter.writeEmptyElement(name);
            } else {
                xmlWriter.writeEmptyElement("", name, "");
            }
        } else {
            xmlWriter.writeEmptyElement(namespaceUri, name);
        }
    }

    private void writeNilElement(@Nullable String namespaceUri, @Nullable String localName) throws XMLStreamException {
        writeStartElement(namespaceUri, localName);
        xmlWriter.writeNamespace("xsi", XSI_NS);
        xmlWriter.writeAttribute("xsi", XSI_NS, "nil", "true");
        xmlWriter.writeEndElement();
    }

    private ObjectScope requireObjectScope() {
        return Objects.requireNonNull(objectScope, "Current XML encoder is not an object scope");
    }

    private ArrayScope requireArrayScope() {
        return Objects.requireNonNull(arrayScope, "Current XML encoder is not an array scope");
    }

    private static PendingProperty requirePendingProperty(ObjectScope object) {
        return Objects.requireNonNull(object.pendingProperty, "No XML property is pending");
    }

    private static final class PendingProperty {
        private final String name;
        private @Nullable String wrappingKey;
        private @Nullable String namespace;
        private @Nullable XmlKey xmlKey;

        private PendingProperty(String name) {
            this.name = name;
        }
    }

    private static final class ObjectScope {
        private boolean rootMapping;
        private boolean ownsElement;
        private boolean started;
        private @Nullable String pendingRootNamespace;
        private final @Nullable PendingProperty ownerProperty;
        private @Nullable PendingProperty pendingProperty;

        private ObjectScope(boolean rootMapping,
                            boolean ownsElement,
                            boolean started,
                            @Nullable PendingProperty ownerProperty) {
            this.rootMapping = rootMapping;
            this.ownsElement = ownsElement;
            this.started = started;
            this.ownerProperty = ownerProperty;
        }

        private static ObjectScope rootMapping() {
            return new ObjectScope(true, false, false, null);
        }

        private static ObjectScope started() {
            return new ObjectScope(false, true, true, null);
        }

        private static ObjectScope deferred(PendingProperty ownerProperty) {
            return new ObjectScope(false, true, false, ownerProperty);
        }
    }

    private static final class ArrayScope {
        private final boolean ownsElement;
        private final @Nullable String itemName;
        private final @Nullable String itemNamespace;
        private final boolean cdata;
        private final XmlNullHandling itemNullHandling;
        private final boolean textList;
        private boolean itemWritten;

        private ArrayScope(boolean ownsElement,
                           @Nullable String itemName,
                           @Nullable String itemNamespace,
                           boolean cdata,
                           XmlNullHandling itemNullHandling,
                           boolean textList) {
            this.ownsElement = ownsElement;
            this.itemName = itemName;
            this.itemNamespace = itemNamespace;
            this.cdata = cdata;
            this.itemNullHandling = itemNullHandling;
            this.textList = textList;
        }
    }
}
