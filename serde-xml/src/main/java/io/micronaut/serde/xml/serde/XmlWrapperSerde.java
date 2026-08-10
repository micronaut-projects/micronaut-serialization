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
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.ObjectSerializer;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.WrappedEncoder;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.exceptions.path.ReferencePath;
import io.micronaut.serde.support.util.PropertySpecificSerde;
import io.micronaut.serde.xml.XmlGenerator;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * XML serde for iterable values that may use a wrapping element.
 * @see io.micronaut.serde.support.serializers.CustomizedIterableSerializer
 *
 * @param <T> The iterable element type
 * @since 3.2
 */
@Internal
public final class XmlWrapperSerde<T> implements
    PropertySpecificSerde<Iterable<T>> {

    @Nullable
    private final Argument<T> generic;
    @Nullable
    private final Serializer<? super T> componentSerializer;
    @Nullable
    private final Deserializer<? extends T> componentDeserializer;
    private final boolean useWrapping;
    @Nullable
    private final String wrapperName;

    XmlWrapperSerde() {
        this.generic = null;
        this.componentSerializer = null;
        this.componentDeserializer = null;
        this.useWrapping = true;
        this.wrapperName = null;
    }

    private XmlWrapperSerde(Argument<T> generic,
                            @Nullable Serializer<? super T> componentSerializer,
                            @Nullable Deserializer<? extends T> componentDeserializer,
                            boolean useWrapping,
                            @Nullable String wrapperName) {
        this.generic = generic;
        this.componentSerializer = componentSerializer;
        this.componentDeserializer = componentDeserializer;
        this.useWrapping = useWrapping;
        this.wrapperName = wrapperName;
    }

    @Override
    public Serializer<Iterable<T>> createSpecific(EncoderContext context,
                                                            Argument<? extends Iterable<T>> type) throws SerdeException {

        final Argument<T> specificGeneric = resolveGeneric(type);
        final Serializer<? super T> specificComponentSerializer = context.findSerializer(specificGeneric)
            .createSpecific(context, specificGeneric);

        return new XmlWrapperSerde<>(
            specificGeneric,
            specificComponentSerializer,
            null,
            useWrapping,
            wrapperName
        );
    }

    @Override
    public Deserializer<Iterable<T>> createSpecific(DecoderContext context,
                                                             Argument<? super Iterable<T>> type) throws SerdeException {
        final Argument<T> specificGeneric = resolveGeneric(type);
        final Deserializer<? extends T> specificComponentDeserializer = context.findDeserializer(specificGeneric)
            .createSpecific(context, specificGeneric);

        return new XmlWrapperSerde<>(
            specificGeneric,
            null,
            specificComponentDeserializer,
            useWrapping,
            wrapperName
        );
    }

    @Override
    public XmlWrapperSerde<T> forProperty(PropertySpecificSerde.PropertyConfiguration configuration) {
        if (generic == null || (componentSerializer == null && componentDeserializer == null)) {
            return this;
        }
        return new XmlWrapperSerde<>(
            generic,
            componentSerializer,
            componentDeserializer,
            configuration.xmlUseWrapping(),
            configuration.xmlWrapperName()
        );
    }

    @Override
    public void serialize(Encoder encoder,
                          EncoderContext context,
                          Argument<? extends Iterable<T>> type,
                          Iterable<T> value) throws IOException {
        XmlGenerator generator = (XmlGenerator) WrappedEncoder.unwrap(encoder);
        if (!type.isContainerType()) {
            throw new SerdeException("Only wrapping container types, not: " + type.getTypeName());
        }
        if (componentSerializer == null || generic == null) {
            throw new SerdeException("XmlWrapperSerde was not specialized for serialization: " + type);
        }

        boolean inlineObjectItems = !(useWrapping && wrapperName != null) && componentSerializer instanceof ObjectSerializer<?>;
        Encoder valuesEncoder = generator;

        if (useWrapping && wrapperName != null) {
            generator.encodeKey(wrapperName);
            generator.encodeArray(type);
        } else if (inlineObjectItems) {
            valuesEncoder = generator.encodeInlineArray(type);
        }

        serializeValues(valuesEncoder, context, type, value, generic, componentSerializer);

        if ((useWrapping && wrapperName != null) || inlineObjectItems) {
            valuesEncoder.finishStructure();
        }
    }

    @Override
    public Iterable<T> deserialize(Decoder decoder,
                                            DecoderContext context,
                                            Argument<? super Iterable<T>> type) throws IOException {
        if (componentDeserializer == null || generic == null) {
            throw new SerdeException("XmlWrapperSerde was not specialized for deserialization: " + type);
        }
        Collection<T> collection = createCollection(type);
        Decoder arrayDecoder = decoder.decodeArray(type);
        int index = 0;
        try {
            while (arrayDecoder.hasNextArrayValue()) {
                collection.add(componentDeserializer.deserializeNullable(arrayDecoder, context, generic));
                index++;
            }
            arrayDecoder.finishStructure();
            return collection;
        } catch (SerdeException e) {
            e.getPath().add(ReferencePath.ofCollection(collection.getClass(), type, index));
            throw e;
        }
    }

    private static <T> void serializeValues(Encoder encoder,
                                            EncoderContext context,
                                            Argument<?> type,
                                            Iterable<T> value,
                                            Argument<T> generic,
                                            Serializer<? super T> componentSerializer) throws IOException {
        int index = 0;
        for (T t : value) {
            try {
                if (t == null) {
                    encoder.encodeNull();
                } else {
                    componentSerializer.serialize(encoder, context, generic, t);
                }
                index++;
            } catch (SerdeException e) {
                e.getPath().add(ReferencePath.ofCollection(value.getClass(), type, index));
                throw e;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> Argument<T> resolveGeneric(Argument<?> type) {
        final Argument<?>[] generics = type.getTypeParameters();
        if (generics.length == 0) {
            return (Argument<T>) Argument.OBJECT_ARGUMENT;
        }
        return (Argument<T>) generics[0];
    }

    private static <T> Collection<T> createCollection(Argument<?> type) {
        if (Set.class.isAssignableFrom(type.getType())) {
            return new LinkedHashSet<>();
        }
        return new ArrayList<>();
    }
}
