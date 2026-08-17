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

import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonStreamConfig;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.util.JsonNodeDecoder;
import io.micronaut.serde.support.util.JsonNodeEncoder;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.XMLStreamWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * The XmlObjectMapper class provides a concrete implementation of the
 * {@link ObjectMapper} interface, utilizing XML serialization and deserialization.
 *
 * @author Mousrij Hamza
 * @since 3.2
 */
@Singleton
@Named(XmlObjectMapper.XML_MAPPER_NAME)
public final class XmlObjectMapper implements ObjectMapper {
    /**
     * The XML object mapper bean qualifier.
     */
    public static final String XML_MAPPER_NAME = "xml";

    private final SerdeRegistry registry;
    @Nullable
    private final SerdeConfiguration serdeConfiguration;
    private final boolean emptyElementAsNull;
    private final XMLInputFactory xmlInputFactory;
    private final XMLOutputFactory xmlOutputFactory;

    /**
     * Constructs an XML object mapper.
     *
     * @param registry The serde registry
     * @param serdeConfiguration The shared serde configuration
     * @param xmlConfiguration The XML serde configuration
     * @throws SerdeException If the XML output factory cannot apply the configured XML features
     */
    public XmlObjectMapper(SerdeRegistry registry,
                           @Nullable SerdeConfiguration serdeConfiguration,
                           @Nullable XmlSerdeConfiguration xmlConfiguration) throws SerdeException {
        this.registry = registry;
        this.serdeConfiguration = serdeConfiguration;
        this.emptyElementAsNull = xmlConfiguration != null
            && xmlConfiguration.isReadFeatureEnabled(XmlSerdeConfiguration.XmlReadFeature.EMPTY_ELEMENT_AS_NULL);
        boolean repairingNamespaces = xmlConfiguration == null || xmlConfiguration.isRepairingNamespaces();
        boolean automaticEmptyElements = xmlConfiguration != null && xmlConfiguration.isAutomaticEmptyElements();
        this.xmlInputFactory = XMLInputFactory.newInstance();
        this.xmlInputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        this.xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        this.xmlInputFactory.setXMLResolver((publicId, systemId, baseUri, namespace) -> {
            throw new XMLStreamException("External XML entities are disabled");
        });
        this.xmlOutputFactory = XMLOutputFactory.newInstance();
        this.xmlOutputFactory.setProperty(XMLOutputFactory.IS_REPAIRING_NAMESPACES, repairingNamespaces);
        try {
            this.xmlOutputFactory.setProperty("org.codehaus.stax2.automaticEmptyElements", automaticEmptyElements);
        } catch (IllegalArgumentException e) {
            if (automaticEmptyElements) {
                throw new SerdeException("XML output factory does not support automatic empty elements", e);
            }
        }
    }

    /**
     * Deserializes an XML stream into an object of the requested type.
     *
     * @param inputStream The XML input stream
     * @param type The target type
     * @param <T> The target type
     * @return The deserialized value
     * @throws IOException If an error occurs reading or decoding XML
     */
    @Override
    public <T> T readValue(InputStream inputStream, Argument<T> type) throws IOException {
        Deserializer.DecoderContext decoderContext = registry.newDecoderContext(null);
        Deserializer<? extends T> deserializer = decoderContext.findDeserializer(type).createSpecific(decoderContext,
            type);

        try (XmlReaderResource resource = new XmlReaderResource(
            xmlInputFactory.createXMLStreamReader(inputStream))) {
            XmlStaxDecoder decoder = new XmlStaxDecoder.DocumentDecoder(
                limits(), resource.reader(), emptyElementAsNull);
            return deserializer.deserialize(decoder, decoderContext, type);
        } catch (XMLStreamException e) {
            throw new SerdeException("Error reading XML", e);
        }
    }

    /**
     * Deserializes XML bytes into an object of the requested type.
     *
     * @param byteArray The XML bytes
     * @param type The target type
     * @param <T> The target type
     * @return The deserialized value
     * @throws IOException If an error occurs reading or decoding XML
     */
    @Override
    public <T> T readValue(byte [] byteArray, Argument<T> type) throws IOException {
        return readValue(new ByteArrayInputStream(byteArray), type);
    }

    /**
     * Deserializes an XML string into an object of the requested type.
     *
     * @param string The XML string
     * @param type The target type
     * @param <T> The target type
     * @return The deserialized value
     * @throws IOException If an error occurs reading or decoding XML
     */
    @Override
    public <T> T readValue(String string, Argument<T> type) throws IOException {
        return readValue(string.getBytes(StandardCharsets.UTF_8), type);
    }

    /**
     * Deserializes a JSON tree into an object of the requested type.
     *
     * @param tree The source tree
     * @param type The target type
     * @param <T> The target type
     * @return The deserialized value
     * @throws IOException If an error occurs decoding the tree
     */
    @Override
    public <T> T readValueFromTree(JsonNode tree, Argument<T> type) throws IOException {
        Deserializer.DecoderContext decoderContext = registry.newDecoderContext(null);
        Deserializer<? extends T> deserializer = decoderContext.findDeserializer(type).createSpecific(decoderContext,
            type);
        return deserializer.deserialize(JsonNodeDecoder.create(tree, limits()), decoderContext, type);
    }

    /**
     * Serializes a value into a JSON tree.
     *
     * @param value The value to serialize
     * @return The serialized tree
     * @throws IOException If an error occurs serializing the value
     */
    @Override
    public JsonNode writeValueToTree(@Nullable Object value) throws IOException {
        return writeValueToTree0(argumentOf(value), value);
    }

    /**
     * Serializes a typed value into a JSON tree.
     *
     * @param type The value type
     * @param value The value to serialize
     * @param <T> The value type
     * @return The serialized tree
     * @throws IOException If an error occurs serializing the value
     */
    @Override
    public <T> JsonNode writeValueToTree(Argument<T> type, @Nullable T value) throws IOException {
        return writeValueToTree0(type, value);
    }

    private JsonNode writeValueToTree0(Argument<?> type, @Nullable Object value) throws IOException {
        JsonNodeEncoder encoder = JsonNodeEncoder.create(limits());
        if (value == null) {
            encoder.encodeNull();
        } else {
            serialize(encoder, value, type);
        }
        return encoder.getCompletedValue();
    }

    /**
     * Serializes a value as XML into an output stream.
     *
     * @param outputStream The destination output stream
     * @param object The value to serialize
     * @throws IOException If an error occurs writing or encoding XML
     */
    @Override
    public void writeValue(OutputStream outputStream, @Nullable Object object) throws IOException {
        writeValue0(outputStream, argumentOf(object), object);
    }

    /**
     * Serializes a typed value as XML into an output stream.
     *
     * @param outputStream The destination output stream
     * @param type The value type
     * @param object The value to serialize
     * @param <T> The value type
     * @throws IOException If an error occurs writing or encoding XML
     */
    @Override
    public <T> void writeValue(OutputStream outputStream, Argument<T> type, @Nullable T object)
        throws IOException {
        writeValue0(outputStream, type, object);
    }

    private void writeValue0(OutputStream outputStream, Argument<?> type, @Nullable Object object) throws IOException {
        try (XmlWriterResource resource = new XmlWriterResource(
            xmlOutputFactory.createXMLStreamWriter(outputStream))) {
            XMLStreamWriter xmlWriter = resource.writer();
            if (object == null) {
                xmlWriter.writeStartDocument();
                xmlWriter.writeEmptyElement("null");
                xmlWriter.writeEndDocument();
                xmlWriter.flush();
            } else {
                XmlStaxEncoder encoder = new XmlStaxEncoder(xmlWriter, resolveRootName(type));
                serialize(encoder, object, type);
            }
        } catch (XMLStreamException e) {
            throw new SerdeException("Error writing XML", e);
        }
    }

    /**
     * Serializes a value as XML bytes.
     *
     * @param object The value to serialize
     * @return The serialized XML bytes
     * @throws IOException If an error occurs writing or encoding XML
     */
    @Override
    public byte[] writeValueAsBytes(@Nullable Object object) throws IOException {
        return writeValueAsBytes0(argumentOf(object), object);
    }

    private byte[] writeValueAsBytes0(Argument<?> type, @Nullable Object object) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeValue0(output, type, object);
        return output.toByteArray();
    }

    /**
     * Serializes a typed value as XML bytes.
     *
     * @param type The value type
     * @param object The value to serialize
     * @param <T> The value type
     * @return The serialized XML bytes
     * @throws IOException If an error occurs writing or encoding XML
     */
    @Override
    public <T> byte[] writeValueAsBytes(Argument<T> type, @Nullable T object) throws IOException {
        return writeValueAsBytes0(type, object);
    }

    /**
     * Serializes a value as an XML string.
     *
     * @param object The value to serialize
     * @return The serialized XML string
     * @throws IOException If an error occurs writing or encoding XML
     */
    @Override
    public String writeValueAsString(@Nullable Object object) throws IOException {
        return new String(writeValueAsBytes(object), StandardCharsets.UTF_8);
    }

    /**
     * @return The stream configuration used by this mapper
     */
    @Override
    public JsonStreamConfig getStreamConfig() {
        return JsonStreamConfig.DEFAULT;
    }

    private LimitingStream.RemainingLimits limits() {
        return serdeConfiguration == null
            ? LimitingStream.DEFAULT_LIMITS
            : LimitingStream.limitsFromConfiguration(serdeConfiguration);
    }

    private static Argument<?> argumentOf(@Nullable Object object) {
        return object == null ? Argument.OBJECT_ARGUMENT : Argument.of(object.getClass());
    }

    @SuppressWarnings("unchecked")
    private void serialize(Encoder encoder, Object object, Argument type) throws IOException {
        Serializer.EncoderContext encoderContext = registry.newEncoderContext(null);
        Serializer<Object> serializer = encoderContext.findSerializer(type).createSpecific(encoderContext, type);
        serializer.serialize(encoder, encoderContext, type, object);
    }

    private static String resolveRootName(Argument<?> type) {
        return type.getAnnotationMetadata()
            .stringValue(SerdeConfig.class, SerdeConfig.WRAPPER_PROPERTY)
            .orElseGet(() -> {
                Class<?> javaType = type.getType();
                if (javaType.isArray()) {
                    return NameUtils.camelCase(javaType.getComponentType().getSimpleName() + "s", false);
                }
                return NameUtils.camelCase(type.getName(), false);
            });
    }

    private record XmlReaderResource(XMLStreamReader reader) implements AutoCloseable {
        @Override
        public void close() throws XMLStreamException {
            reader.close();
        }
    }

    private record XmlWriterResource(XMLStreamWriter writer) implements AutoCloseable {
        @Override
        public void close() throws XMLStreamException {
            writer.close();
        }
    }
}
