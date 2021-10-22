package io.micronaut.serde;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
import jakarta.inject.Singleton;

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
        final Collection<BeanDefinition<Serializer>> serializers = beanContext.getBeanDefinitions(Serializer.class);
        final Collection<BeanDefinition<Deserializer>> deserializers = beanContext.getBeanDefinitions(Deserializer.class);
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
        final Deserializer<Boolean> booleanDeserializer =
                (decoder, decoderContext, type, generics) -> decoder.decodeBoolean();
        this.deserializerMap.put(
                new TypeEntry(Argument.BOOLEAN),
                booleanDeserializer
        );
        this.deserializerMap.put(
                new TypeEntry(Argument.of(Boolean.class)),
                booleanDeserializer
        );
        final Deserializer<Integer> integerDeserializer =
                (decoder, decoderContext, type, generics) -> decoder.decodeInt();
        this.deserializerMap.put(
                new TypeEntry(Argument.INT),
                integerDeserializer
        );
        this.deserializerMap.put(
                new TypeEntry(Argument.of(Integer.class)),
                integerDeserializer
        );
        final Deserializer<Byte> byteDeserializer =
                (decoder, decoderContext, type, generics) -> decoder.decodeByte();
        this.deserializerMap.put(
                new TypeEntry(Argument.BYTE),
                byteDeserializer
        );
        this.deserializerMap.put(
                new TypeEntry(Argument.of(Byte.class)),
                byteDeserializer
        );
        final Deserializer<Short> shortDeserializer =
                (decoder, decoderContext, type, generics) -> decoder.decodeShort();
        this.deserializerMap.put(
                new TypeEntry(Argument.SHORT),
                shortDeserializer
        );
        this.deserializerMap.put(
                new TypeEntry(Argument.of(Short.class)),
                shortDeserializer
        );
        final Deserializer<Long> longDeserializer =
                (decoder, decoderContext, type, generics) -> decoder.decodeLong();
        this.deserializerMap.put(
                new TypeEntry(Argument.LONG),
                longDeserializer
        );
        this.deserializerMap.put(
                new TypeEntry(Argument.of(Long.class)),
                longDeserializer
        );
        final Deserializer<Float> floatDeserializer = (decoder, decoderContext, type, generics) -> decoder.decodeFloat();
        this.deserializerMap.put(
                new TypeEntry(Argument.FLOAT),
                floatDeserializer
        );
        this.deserializerMap.put(
                new TypeEntry(Argument.of(Float.class)),
                floatDeserializer
        );
        final Deserializer<Double> doubleDeserializer =
                (decoder, decoderContext, type, generics) -> decoder.decodeDouble();
        this.deserializerMap.put(
                new TypeEntry(Argument.DOUBLE),
                doubleDeserializer
        );
        this.deserializerMap.put(
                new TypeEntry(Argument.of(Double.class)),
                doubleDeserializer
        );
        final Deserializer<Character> characterDeserializer =
                (decoder, decoderContext, type, generics) -> decoder.decodeChar();
        this.deserializerMap.put(
                new TypeEntry(Argument.CHAR),
                characterDeserializer
        );
        this.deserializerMap.put(
                new TypeEntry(Argument.of(Character.class)),
                characterDeserializer
        );
        this.objectSerializer = objectSerializer;
        this.objectDeserializer = objectDeserializer;
    }

    @Override
    public <T> Deserializer<? extends T> findDeserializer(Argument<? extends T> type) throws SerdeException {
        Objects.requireNonNull(type, "Type cannot be null");
        final TypeEntry key = new TypeEntry(type);
        final Deserializer<?> deserializer = deserializerMap.get(key);
        if (deserializer != null) {
            return (Deserializer<? extends T>) deserializer;
        } else {
            final List<BeanDefinition<Deserializer>> possibles = deserializerDefMap.get(type.getType());
            if (possibles != null) {
                if (type.hasTypeVariables()) {
                    // narrow by generics
                } else if (possibles.size() == 1) {
                    final Deserializer deser = beanContext.getBean(possibles.iterator().next());
                    deserializerMap.put(key, deser);
                    return deser;
                } else {
                    throw new SerdeException("Multiple possible deserializers for type [" + type + "]: " + possibles);
                }
            }
        }
        return (Deserializer<? extends T>) objectDeserializer;
    }

    @Override
    public <T> BeanIntrospection<T> getDeserializableIntrospection(Argument<T> type) {
        return introspections.getDeserializableIntrospection(type);
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



    @Override
    public <T> BeanIntrospection<T> getSerializableIntrospection(Argument<T> type) {
        return introspections.getSerializableIntrospection(type);
    }

    private final static class TypeEntry {
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
