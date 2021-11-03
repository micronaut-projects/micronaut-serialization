/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.serde;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.ConcurrentHashMap;

import io.micronaut.context.BeanContext;
import io.micronaut.context.exceptions.ConfigurationException;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.util.NullableDeserializer;
import jakarta.inject.Singleton;

/**
 * Default implementation of the {@link io.micronaut.serde.SerdeRegistry} interface.
 */
@Singleton
public class DefaultSerdeRegistry implements SerdeRegistry {

    private final Serializer<Object> objectSerializer;
    private final Map<Class<?>, List<BeanDefinition<Serializer>>> serializerDefMap;
    private final Map<Class<?>, List<BeanDefinition<Deserializer>>> deserializerDefMap;
    private final Map<TypeEntry, Serializer<?>> serializerMap = new ConcurrentHashMap<>(50);
    private final Map<TypeEntry, Deserializer<?>> deserializerMap = new ConcurrentHashMap<>(50);
    private final BeanContext beanContext;
    private final SerdeIntrospections introspections;
    private final Deserializer<Object> objectDeserializer;

    public DefaultSerdeRegistry(
            BeanContext beanContext,
            Serializer<Object> objectSerializer,
            Deserializer<Object> objectDeserializer,
            SerdeIntrospections introspections) {
        final Collection<BeanDefinition<Serializer>> serializers =
                beanContext.getBeanDefinitions(Serializer.class);
        final Collection<BeanDefinition<Deserializer>> deserializers =
                beanContext.getBeanDefinitions(Deserializer.class);
        this.introspections = introspections;
        this.serializerDefMap = new HashMap<>(serializers.size());
        this.deserializerDefMap = new HashMap<>(deserializers.size());
        this.beanContext = beanContext;
        for (BeanDefinition<Serializer> serializer : serializers) {
            final List<Argument<?>> typeArguments = serializer.getTypeArguments(Serializer.class);
            if (CollectionUtils.isNotEmpty(typeArguments)) {
                final Argument<?> argument = typeArguments.iterator().next();
                if (!argument.equalsType(Argument.OBJECT_ARGUMENT)) {
                    final Class<?> t = argument.getType();
                    serializerDefMap
                            .computeIfAbsent(t, aClass -> new ArrayList<>(5))
                            .add(serializer);
                    final Class<?> primitiveType = ReflectionUtils.getPrimitiveType(t);
                    if (primitiveType != t) {
                        serializerDefMap
                                .computeIfAbsent(primitiveType, aClass -> new ArrayList<>(5))
                                .add(serializer);
                    }
                }
            } else {
                throw new  ConfigurationException("Serializer without generic types defined: " + serializer.getBeanType());
            }
        }
        for (BeanDefinition<Deserializer> deserializer : deserializers) {
            final List<Argument<?>> typeArguments = deserializer.getTypeArguments(Deserializer.class);
            if (CollectionUtils.isNotEmpty(typeArguments)) {
                final Argument<?> argument = typeArguments.iterator().next();
                if (!argument.equalsType(Argument.OBJECT_ARGUMENT)) {
                    final Class<?> t = argument.getType();
                    deserializerDefMap
                            .computeIfAbsent(t, aClass -> new ArrayList<>(5))
                            .add(deserializer);
                    final Class<?> primitiveType = ReflectionUtils.getPrimitiveType(t);
                    if (primitiveType != t) {
                        deserializerDefMap
                                .computeIfAbsent(primitiveType, aClass -> new ArrayList<>(5))
                                .add(deserializer);
                    }
                }
            } else {
                throw new ConfigurationException("Deserializer without generic types defined: " + deserializer.getBeanType());
            }
        }
        registerPrimitiveOptionalSerdes();
        this.deserializerMap.put(new TypeEntry(Argument.STRING),
                                 (NullableDeserializer<String>) (decoder, decoderContext, type) -> decoder.decodeString());
        this.deserializerMap.put(new TypeEntry(Argument.of(BigDecimal.class)),
                                 (NullableDeserializer<BigDecimal>) (decoder, decoderContext, type) -> decoder.decodeBigDecimal());
        this.deserializerMap.put(new TypeEntry(Argument.of(BigInteger.class)),
                                 (NullableDeserializer<BigInteger>) (decoder, decoderContext, type) -> decoder.decodeBigInteger());
        this.deserializerMap.put(
                new TypeEntry(Argument.BOOLEAN),
                (decoder, decoderContext, type) -> decoder.decodeBoolean()
        );
        this.deserializerMap.put(
                new TypeEntry(Argument.of(Boolean.class)),
                (NullableDeserializer<Boolean>) (decoder, decoderContext, type) -> decoder.decodeBoolean()
        );
        this.deserializerMap.put(
                new TypeEntry(Argument.INT),
                (decoder, decoderContext, type) -> decoder.decodeInt()
        );
        this.deserializerMap.put(
                new TypeEntry(Argument.of(Integer.class)),
                (NullableDeserializer<Integer>) (decoder, decoderContext, type) -> decoder.decodeInt()
        );
        this.deserializerMap.put(
                new TypeEntry(Argument.BYTE),
                (decoder, decoderContext, type) -> decoder.decodeByte()
        );
        this.deserializerMap.put(
                new TypeEntry(Argument.of(Byte.class)),
                (NullableDeserializer<Byte>) (decoder, decoderContext, type) -> decoder.decodeByte()
        );
        this.deserializerMap.put(
                new TypeEntry(Argument.SHORT),
                (decoder, decoderContext, type) -> decoder.decodeShort()
        );
        this.deserializerMap.put(
                new TypeEntry(Argument.of(Short.class)),
                (NullableDeserializer<Short>) (decoder, decoderContext, type) -> decoder.decodeShort()
        );
        this.deserializerMap.put(
                new TypeEntry(Argument.LONG),
                (decoder, decoderContext, type) -> decoder.decodeLong()
        );
        this.deserializerMap.put(
                new TypeEntry(Argument.of(Long.class)),
                (NullableDeserializer<Long>) (decoder, decoderContext, type) -> decoder.decodeLong()
        );
        this.deserializerMap.put(
                new TypeEntry(Argument.FLOAT),
                (decoder, decoderContext, type) -> decoder.decodeFloat()
        );
        this.deserializerMap.put(
                new TypeEntry(Argument.of(Float.class)),
                (NullableDeserializer<Float>) (decoder, decoderContext, type) -> decoder.decodeFloat()
        );
        this.deserializerMap.put(
                new TypeEntry(Argument.DOUBLE),
                (decoder, decoderContext, type) -> decoder.decodeDouble()
        );
        this.deserializerMap.put(
                new TypeEntry(Argument.of(Double.class)),
                (NullableDeserializer<Double>) (decoder, decoderContext, type) -> decoder.decodeDouble()
        );
        this.deserializerMap.put(
                new TypeEntry(Argument.CHAR),
                (decoder, decoderContext, type) -> decoder.decodeChar()
        );
        this.deserializerMap.put(
                new TypeEntry(Argument.of(Character.class)),
                (NullableDeserializer<Character>) (decoder, decoderContext, type) -> decoder.decodeChar()
        );
        this.objectSerializer = objectSerializer;
        this.objectDeserializer = objectDeserializer;
    }

    private void registerPrimitiveOptionalSerdes() {
        final TypeEntry optionalIntKey = new TypeEntry(Argument.of(OptionalInt.class));
        this.deserializerMap.put(optionalIntKey, new Deserializer<OptionalInt>() {
            @Override
            public OptionalInt deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super OptionalInt> type)
                    throws IOException {
                if (decoder.decodeNull()) {
                    return OptionalInt.empty();
                } else {
                    return OptionalInt.of(
                        decoder.decodeInt()
                    );
                }
            }

            @Override
            public OptionalInt getDefaultValue() {
                return OptionalInt.empty();
            }
        });
        this.serializerMap.put(optionalIntKey, new Serializer<OptionalInt>() {
            @Override
            public void serialize(Encoder encoder,
                                  EncoderContext context,
                                  OptionalInt value,
                                  Argument<? extends OptionalInt> type) throws IOException {
                 if (value.isPresent()) {
                     encoder.encodeInt(value.getAsInt());
                 } else {
                     encoder.encodeNull();
                 }
            }

            @Override
            public boolean isEmpty(OptionalInt value) {
                return value == null || !value.isPresent();
            }

            @Override
            public boolean isAbsent(OptionalInt value) {
                return value == null || !value.isPresent();
            }
        });

        final TypeEntry optionalDoubleKey = new TypeEntry(Argument.of(OptionalDouble.class));
        this.deserializerMap.put(optionalDoubleKey, new Deserializer<OptionalDouble>() {
            @Override
            public OptionalDouble deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super OptionalDouble> type)
                    throws IOException {
                if (decoder.decodeNull()) {
                    return OptionalDouble.empty();
                } else {
                    return OptionalDouble.of(
                            decoder.decodeDouble()
                    );
                }
            }

            @Override
            public OptionalDouble getDefaultValue() {
                return OptionalDouble.empty();
            }
        });
        this.serializerMap.put(optionalDoubleKey, new Serializer<OptionalDouble>() {
            @Override
            public void serialize(Encoder encoder,
                                  EncoderContext context,
                                  OptionalDouble value,
                                  Argument<? extends OptionalDouble> type) throws IOException {
                if (value.isPresent()) {
                    encoder.encodeDouble(value.getAsDouble());
                } else {
                    encoder.encodeNull();
                }
            }

            @Override
            public boolean isEmpty(OptionalDouble value) {
                return value == null || !value.isPresent();
            }

            @Override
            public boolean isAbsent(OptionalDouble value) {
                return value == null || !value.isPresent();
            }
        });

        final TypeEntry optionalLongKey = new TypeEntry(Argument.of(OptionalLong.class));
        this.deserializerMap.put(optionalLongKey, new Deserializer<OptionalLong>() {
            @Override
            public OptionalLong deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super OptionalLong> type)
                    throws IOException {
                if (decoder.decodeNull()) {
                    return OptionalLong.empty();
                } else {
                    return OptionalLong.of(
                            decoder.decodeLong()
                    );
                }
            }

            @Override
            public OptionalLong getDefaultValue() {
                return OptionalLong.empty();
            }
        });
        this.serializerMap.put(optionalLongKey, new Serializer<OptionalLong>() {
            @Override
            public void serialize(Encoder encoder,
                                  EncoderContext context,
                                  OptionalLong value,
                                  Argument<? extends OptionalLong> type) throws IOException {
                if (value.isPresent()) {
                    encoder.encodeLong(value.getAsLong());
                } else {
                    encoder.encodeNull();
                }
            }

            @Override
            public boolean isEmpty(OptionalLong value) {
                return value == null || !value.isPresent();
            }

            @Override
            public boolean isAbsent(OptionalLong value) {
                return value == null || !value.isPresent();
            }
        });
    }

    @Override
    public <T> Deserializer<? extends T> findDeserializer(Argument<? extends T> type) throws SerdeException {
        Objects.requireNonNull(type, "Type cannot be null");
        final TypeEntry key = new TypeEntry(type);
        final Deserializer<?> deserializer = deserializerMap.get(key);
        if (deserializer != null) {
            return (Deserializer<? extends T>) deserializer;
        } else {
            final Deserializer<?> deser = beanContext.findBean(Argument.of(Deserializer.class, type))
                    .orElse(null);
            if (deser != null) {
                deserializerMap.put(key, deser);
                return (Deserializer<? extends T>) deser;
            }
        }
        return (Deserializer<? extends T>) objectDeserializer;
    }

    @Override
    public <T> Collection<BeanIntrospection<? extends T>> getDeserializableSubtypes(Class<T> superType) {
        return introspections.findSubtypeDeserializables(superType);
    }

    @Override
    public <T> Serializer<? super T> findSerializer(Argument<? extends T> type) throws SerdeException {
        Objects.requireNonNull(type, "Type cannot be null");
        final TypeEntry key = new TypeEntry(type);
        final Serializer<?> serializer = serializerMap.get(key);
        if (serializer != null) {
            //noinspection unchecked
            return (Serializer<? super T>) serializer;
        } else {
            List<BeanDefinition<Serializer>> possibles = serializerDefMap.get(type.getType());
            if (possibles == null) {
                for (Map.Entry<Class<?>, List<BeanDefinition<Serializer>>> entry : serializerDefMap.entrySet()) {
                    final Class<?> targetType = entry.getKey();
                    if (targetType.isAssignableFrom(type.getType())) {
                        possibles = entry.getValue();
                        final Argument<?>[] params = type.getTypeParameters();
                        if (ArrayUtils.isNotEmpty(params)) {
                            // narrow for generics
                            possibles = new ArrayList<>(possibles);
                            final Iterator<BeanDefinition<Serializer>> i = possibles.iterator();
                            while (i.hasNext()) {
                                final BeanDefinition<Serializer> bd = i.next();
                                final Argument<?>[] candidateParams = bd.getTypeArguments(Serializer.class).get(0)
                                        .getTypeParameters();
                                if (candidateParams.length == params.length) {
                                    for (int j = 0; j < params.length; j++) {
                                        Argument<?> param = params[j];
                                        final Argument<?> candidateParam = candidateParams[j];
                                        if (!((param.getType() == candidateParam.getType()) ||
                                                      (candidateParam.isTypeVariable() && candidateParam.getType().isAssignableFrom(param.getType())))) {
                                            i.remove();
                                        }
                                    }
                                } else {
                                    i.remove();
                                }
                            }
                        }
                        break;
                    }
                }
            }
            if (possibles != null) {
                if (possibles.size() == 1) {
                    final BeanDefinition<Serializer> definition = possibles.iterator().next();
                    final Serializer locatedSerializer = beanContext.getBean(definition);
                    serializerMap.put(key, locatedSerializer);
                    return locatedSerializer;
                } else if (possibles.isEmpty()) {
                    throw new SerdeException("No serializers found for type: " + type);
                } else {
                    throw new SerdeException("Multiple possible serializers found for type [" + type + "]: " + possibles);
                }
            } else {
                serializerMap.put(key, objectSerializer);
            }
        }
        return objectSerializer;
    }

    private static final class TypeEntry {
        final Argument<?> type;

        public TypeEntry(Argument<?> type) {
            this.type = type;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            TypeEntry that = (TypeEntry) o;
            return type.equalsType(that.type);
        }

        @Override
        public int hashCode() {
            return type.typeHashCode();
        }
    }
}
