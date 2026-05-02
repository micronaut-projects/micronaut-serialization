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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonStreamConfig;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.SerdeIntrospections;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.support.util.JsonNodeDecoder;
import io.micronaut.serde.support.util.JsonNodeEncoder;
import io.micronaut.serde.xml.annotation.XmlRootName;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import tools.jackson.dataformat.xml.XmlFactory;
import tools.jackson.dataformat.xml.deser.FromXmlParser;
import tools.jackson.dataformat.xml.ser.ToXmlGenerator;
import javax.xml.stream.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * The XmlObjectMapper class provides a concrete implementation of the
 * {@link ObjectMapper} interface,
 * utilizing XML serialization and deserialization.
 *
 * @author Mousrij Hamza
 *
 */
@Singleton
@Named("xml")
@Internal
public final class XmlObjectMapper implements ObjectMapper {

    private final SerdeRegistry registry;
    private final SerdeIntrospections introspections;
    private final XmlFactory xmlFactory;
    @Nullable
    private final SerdeConfiguration serdeConfiguration;
    @Nullable
    private final String defaultRootName;
    @NonNull
    private final XMLInputFactory xmlInputFactory;
    @NonNull
    private final XMLOutputFactory xmlOutputFactory;
    @Nullable
    private final XmlSerdeConfiguration xmlConfiguration;

    public XmlObjectMapper(SerdeRegistry registry,
                           SerdeIntrospections introspections,
                           @Nullable SerdeConfiguration serdeConfiguration,
                           @Nullable XmlSerdeConfiguration xmlConfiguration) {
        this.registry = registry;
        this.introspections = introspections;
        this.serdeConfiguration = serdeConfiguration;
        this.xmlConfiguration = xmlConfiguration;
        this.xmlFactory = XmlFactory.builder().build();
        this.defaultRootName = xmlConfiguration != null ? xmlConfiguration.getDefaultRootName() : null;
        this.xmlOutputFactory = XMLOutputFactory.newInstance();
        // Woodstox (and any stax2-api compliant factory) auto-converts empty <x></x> elements
        // into self-closing <x/>. Our textual XML contracts treat `<x></x>` and `<x/>` as
        // distinct on the encode side (they are equivalent on the read side), so opt out of
        // the Woodstox optimization. The property is silently ignored on factories that don't
        // recognize it (e.g. the JDK's built-in StAX writer, which already emits `<x></x>`).
        try {
            this.xmlOutputFactory.setProperty("org.codehaus.stax2.automaticEmptyElements", false);
        } catch (IllegalArgumentException ignored) {
            // Factory doesn't recognize the property — its default behavior already matches.
        }
        this.xmlInputFactory = XMLInputFactory.newInstance();
    }

    @Override
    public <T> T readValue(@NonNull InputStream inputStream, @NonNull Argument<T> type) throws IOException {
        Deserializer.DecoderContext decoderContext = registry.newDecoderContext(null);
        Deserializer<? extends T> deserializer = decoderContext.findDeserializer(type).createSpecific(decoderContext,
            type);

        XMLStreamReader xmlReader = null;
        try {
            xmlReader = xmlInputFactory.createXMLStreamReader(inputStream);
            XmlReader decoder = new XmlReader(limits(), xmlReader);
            deserializer.deserialize(decoder, decoderContext, type);
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
        return readValue(toByteArray(inputStream), type);
    }

    @Override
    public <T> T readValue(byte @NonNull [] byteArray, @NonNull Argument<T> type) throws IOException {
        return readValue(new ByteArrayInputStream(byteArray), type);
    }

    @Override
    public <T> T readValue(@NonNull String string, @NonNull Argument<T> type) throws IOException {
        return readValue(string.getBytes(StandardCharsets.UTF_8), type);
    }

    @Override
    public <T> T readValueFromTree(@NonNull JsonNode tree, @NonNull Argument<T> type) throws IOException {
        Deserializer.DecoderContext decoderContext = registry.newDecoderContext(null);
        Deserializer<? extends T> deserializer = decoderContext.findDeserializer(type).createSpecific(decoderContext,
            type);
        return deserializer.deserialize(JsonNodeDecoder.create(tree, limits()), decoderContext, type);
    }

    @Override
    public @NonNull JsonNode writeValueToTree(@Nullable Object value) throws IOException {
        JsonNodeEncoder encoder = JsonNodeEncoder.create(limits());
        serialize(encoder, value, Argument.of(value.getClass()));
        return encoder.getCompletedValue();
    }

    @Override
    public @NonNull <T> JsonNode writeValueToTree(@NonNull Argument<T> type, @Nullable T value) throws IOException {
        JsonNodeEncoder encoder = JsonNodeEncoder.create(limits());
        serialize(encoder, value, type);
        return encoder.getCompletedValue();
    }

    @Override
    public void writeValue(@NonNull OutputStream outputStream, @Nullable Object object) throws IOException {
        if (object == null) {
            try (ToXmlGenerator generator = createGenerator(outputStream)) {
                generator.writeNull();
                generator.flush();
            }
            return;
        }
        Argument<?> type = Argument.of(object.getClass());
        XMLStreamWriter xmlWriter = null;
        try {
            xmlWriter = xmlOutputFactory.createXMLStreamWriter(outputStream);
            XmlGenerator encoder = new XmlGenerator(xmlWriter);
            serialize(encoder, object, type);
            xmlWriter.close();
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> void writeValue(@NonNull OutputStream outputStream, @NonNull Argument<T> type, @Nullable T object)
        throws IOException {
        XMLStreamWriter xmlWriter = null;
        try {
            xmlWriter = xmlOutputFactory.createXMLStreamWriter(outputStream);
            XmlGenerator encoder = new XmlGenerator(xmlWriter);
            serialize(encoder, object, type);
            xmlWriter.close();
        } catch (XMLStreamException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] writeValueAsBytes(@Nullable Object object) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeValue(output, object);
        return output.toByteArray();
    }

    @Override
    public <T> byte[] writeValueAsBytes(@NonNull Argument<T> type, @Nullable T object) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeValue(output, type, object);
        return output.toByteArray();
    }

    @Override
    public @NonNull String writeValueAsString(@Nullable Object object) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeValue(output, object);
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    @Override
    public @NonNull JsonStreamConfig getStreamConfig() {
        return JsonStreamConfig.DEFAULT;
    }

    @NonNull
    private LimitingStream.RemainingLimits limits() {
        return serdeConfiguration == null
            ? LimitingStream.DEFAULT_LIMITS
            : LimitingStream.limitsFromConfiguration(serdeConfiguration);
    }

    @SuppressWarnings("unchecked")
    private void serialize(Encoder encoder, Object object, Argument type) throws IOException {
        Serializer.EncoderContext encoderContext = registry.newEncoderContext(null);
        Serializer<Object> serializer = encoderContext.findSerializer(type).createSpecific(encoderContext, type);
        serializer.serialize(encoder, encoderContext, type, object);
    }

    private FromXmlParser createParser(byte[] byteArray) {
        return (FromXmlParser) xmlFactory.createParser(
            tools.jackson.core.ObjectReadContext.empty(),
            byteArray);
    }

    private ToXmlGenerator createGenerator(OutputStream outputStream) {
        return (ToXmlGenerator) xmlFactory.createGenerator(
            tools.jackson.core.ObjectWriteContext.empty(),
            outputStream);
    }

    private byte[] toByteArray(InputStream inputStream) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[512];
        int bytesRead;
        while ((bytesRead = inputStream.read(buffer)) != -1) {
            baos.write(buffer, 0, bytesRead);
        }
        return baos.toByteArray();
    }

    private String resolveRootName(Argument<?> type) {

        String annotationRootName = null;
        try {
            BeanIntrospection<?> introspection = introspections.getSerializableIntrospection(type);
            annotationRootName = introspection.stringValue(XmlRootName.class).orElse(null);
        } catch (Exception ignored) {
            // fallback to defaults
        }
        if (annotationRootName != null && !annotationRootName.isBlank()) {
            return annotationRootName;
        }
        if (defaultRootName != null) {
            return defaultRootName;
        }
        String name = type.getSimpleName();
        return (name == null || name.isEmpty()) ? "root" : name;
    }
}
