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
import io.micronaut.serde.Encoder;
import io.micronaut.serde.ObjectSerializer;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.exceptions.path.ReferencePath;
import io.micronaut.serde.xml.XmlGenerator;
import org.jspecify.annotations.NonNull;
import java.io.IOException;

import static io.micronaut.serde.support.util.SerdeArgumentConf.reconstructGenericWithParentMetadata;

public final class XmlWrapperSerde<T> implements Serializer<Iterable<T>> {

    private final Argument<T> generic;
    private final Serializer<? super T> componentSerializer;

    public XmlWrapperSerde() {
        this.generic = null;
        this.componentSerializer = null;
    }

    private XmlWrapperSerde(Argument<T> generic, Serializer<? super T> componentSerializer) {
        this.generic = generic;
        this.componentSerializer = componentSerializer;
    }

    @Override
    public @NonNull Serializer<Iterable<T>> createSpecific(@NonNull EncoderContext context,
                                                            @NonNull Argument<? extends Iterable<T>> type) throws SerdeException {

        final Argument<?>[] generics = type.getTypeParameters();

        final Argument<T> specificGeneric = reconstructGenericWithParentMetadata(type, (Argument<T>) generics[0]);
        final Serializer<? super T> specificComponentSerializer = context.findSerializer(specificGeneric)
            .createSpecific(context, specificGeneric);

        return new XmlWrapperSerde<>(specificGeneric, specificComponentSerializer);
    }

    @Override
    public void serialize(@NonNull Encoder encoder,
                          @NonNull EncoderContext context,
                          @NonNull Argument<? extends Iterable<T>> type,
                          @NonNull Iterable<T> value) throws IOException {
        System.out.println("is container type :" + !type.isContainerType());
        if (!type.isContainerType()) {
            throw new SerdeException("Only wrapping container types, not: " + type.getTypeName());
        }
        if (componentSerializer == null || generic == null) {
            throw new SerdeException("XmlWrapperSerde was not specialized for: " + type);
        }

        var metadata = type.getAnnotationMetadata();
        boolean useWrapping = metadata.booleanValue(SerdeConfig.class, SerdeConfig.META_ANNOTATION_PROPERTY).orElse(true);
        var wrappingConfig = metadata.stringValue(SerdeConfig.class, SerdeConfig.WRAPPER_PROPERTY);

        boolean inlineObjectItems = !useWrapping && componentSerializer instanceof ObjectSerializer<?>;
        Encoder valuesEncoder = encoder;

        if (useWrapping) {
            wrappingConfig.ifPresent(wrappingName -> {
                try {
                    encoder.encodeKey(wrappingName);
                    encoder.encodeArray(type);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        } else if (inlineObjectItems) {
            valuesEncoder = ((XmlGenerator) encoder).encodeInlineArray(type);
        }

        serializeValues(valuesEncoder, context, type, value, generic, componentSerializer);

        if (useWrapping || inlineObjectItems) {
            valuesEncoder.finishStructure();
        }

    }

    private static <T> void serializeValues(Encoder encoder,
                                            EncoderContext context,
                                            Argument<? extends Iterable<T>> type,
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
}
