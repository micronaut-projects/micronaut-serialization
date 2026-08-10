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
package io.micronaut.serde.xml.serde;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serde;
import io.micronaut.serde.WrappedDecoder;
import io.micronaut.serde.WrappedEncoder;
import io.micronaut.serde.support.util.PropertySpecificSerde;
import io.micronaut.serde.xml.XmlGenerator;
import io.micronaut.serde.xml.XmlReaderDecoder;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Objects;

/**
 * XML serde for properties mapped to XML attributes, for example properties
 * annotated with {@code @JacksonXmlProperty(isAttribute = true)} and rendered as
 * {@code <xml a=""/>}.
 *
 * @param <T> The property type
 * @since 3.2
 */
@Internal
public class XmlPropertySerde<T> implements PropertySpecificSerde<T> {

    private final @Nullable String localName;
    private final @Nullable String namespace;

    XmlPropertySerde() {
        this(null, null);
    }

    private XmlPropertySerde(@Nullable String localName, @Nullable String namespace) {
        this.localName = localName;
        this.namespace = namespace;
    }

    @Override
    public Serde<T> forProperty(PropertySpecificSerde.PropertyConfiguration configuration) {
        String localName = configuration.name();
        String resolvedNamespace = configuration.xmlNamespace();
        if (localName.equals(this.localName) && Objects.equals(resolvedNamespace, this.namespace)) {
            return this;
        }
        return new XmlPropertySerde<>(localName, resolvedNamespace);
    }

    @Override
    public T deserialize(Decoder decoder,
                                  DecoderContext context,
                                  Argument<? super T> type) throws IOException {
        String value = decodeAttributeValue(decoder);
        if (value == null) {
            throw decoder.createDeserializationException("Missing XML attribute value for: " + type, null);
        }
        return convert(value, context, type);
    }

    @Override
    public @Nullable T deserializeNullable(Decoder decoder,
                                           DecoderContext context,
                                           Argument<? super T> type) throws IOException {
        String value = decodeAttributeValue(decoder);
        return value == null ? null : convert(value, context, type);
    }

    private static <T> T convert(String value,
                                          DecoderContext context,
                                          Argument<? super T> type) {
        if (type.isInstance(value)) {
            return (T) value;
        }
        return (T) context.getConversionService().convertRequired(value, type);
    }

    /**
     * Pulls the current attribute value through the XML decoder hook, falling back to a plain
     * scalar read for non-XML decoders.
     *
     * @param decoder The decoder
     * @return The attribute value, or {@code null} if absent
     * @throws IOException If an error occurs while decoding
     */
    private static @Nullable String decodeAttributeValue(Decoder decoder) throws IOException {
        decoder = WrappedDecoder.unwrap(decoder);
        if (decoder instanceof XmlReaderDecoder xmlDecoder) {
            return xmlDecoder.decodeCurrentXmlAttribute();
        }
        return decoder.decodeStringNullable();
    }

    @Override
    public void serialize(Encoder encoder,
                          EncoderContext context,
                          Argument<? extends T> type,
                          T value) throws IOException {
        XmlGenerator generator = (XmlGenerator) WrappedEncoder.unwrap(encoder);
        if (value == null) {
            generator.encodeNull();
            return;
        }
        String attributeName = localName == null ? type.getName() : localName;
        if (namespace != null) {
            generator.writeNamespacedAttributeForCurrentKey(namespace, attributeName, String.valueOf(value));
        } else {
            generator.writeAttributeForCurrentKey(attributeName, String.valueOf(value));
        }
    }
}
