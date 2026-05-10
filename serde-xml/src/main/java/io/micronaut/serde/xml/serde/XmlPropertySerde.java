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

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Serde;
import io.micronaut.serde.XmlElementSerde;
import io.micronaut.serde.xml.XmlGenerator;
import io.micronaut.serde.xml.XmlReaderDecoder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

/**
 * XML serde for properties mapped to XML attributes, for example properties
 * annotated with {@code @JacksonXmlProperty(isAttribute = true)} and rendered as
 * {@code <xml a=""/>}.
 *
 * @param <T> The property type
 * @since 3.0.0
 */
public class XmlPropertySerde<T> extends XmlSerde<T> implements XmlElementSerde<T> {

    private final @Nullable String namespace;

    public XmlPropertySerde() {
        this(null);
    }

    private XmlPropertySerde(@Nullable String namespace) {
        this.namespace = namespace;
    }

    /**
     * Returns a variant bound to the given namespace, or {@code this} when none is configured.
     *
     * @param localName The resolved local element name
     * @param namespace The namespace URI, or {@code null}/empty when none
     * @return The configured serde; never {@code null}
     */
    @Override
    public @NonNull Serde<T> withXmlElement(@NonNull String localName, @Nullable String namespace) {
        if (namespace == null || namespace.isEmpty()) {
            return this;
        }
        return new XmlPropertySerde<>(namespace);
    }

    @Override
    public @Nullable T deserialize(@NonNull Decoder decoder,
                                   @NonNull DecoderContext context,
                                   @NonNull Argument<? super T> type) throws IOException {
        String value = decodeAttributeValue(decoder);
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return (T) value;
        }
        return (T) context.getConversionService().convertRequired(value, type);
    }

    @Override
    public @Nullable T deserializeNullable(@NonNull Decoder decoder,
                                           @NonNull DecoderContext context,
                                           @NonNull Argument<? super T> type) throws IOException {
        return deserialize(decoder, context, type);
    }

    /**
     * Pulls the current attribute value through the XML decoder hook, falling back to a plain
     * scalar read for non-XML decoders.
     *
     * @param decoder The decoder
     * @return The attribute value, or {@code null} if absent
     * @throws IOException If an error occurs while decoding
     */
    private static @Nullable String decodeAttributeValue(@NonNull Decoder decoder) throws IOException {
        if (decoder instanceof XmlReaderDecoder xmlDecoder) {
            return xmlDecoder.decodeCurrentXmlAttribute();
        }
        return decoder.decodeStringNullable();
    }

    @Override
    protected void doSerialize(XmlGenerator encoder, EncoderContext context, T value, Argument<?> key) throws IOException {
        if (value == null) {
            encoder.encodeNull();
            return;
        }
        if (namespace != null) {
            encoder.writeNamespacedAttributeForCurrentKey(namespace, String.valueOf(value));
        } else {
            encoder.writeAttributeForCurrentKey(String.valueOf(value));
        }
    }
}
