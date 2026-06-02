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
package io.micronaut.serde.jsonb;

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.BeanReadProperty;
import io.micronaut.core.beans.BeanWriteProperty;
import io.micronaut.core.beans.UnsafeBeanReadProperty;
import io.micronaut.core.beans.UnsafeBeanWriteProperty;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.serde.config.annotation.SerdeConfig;
import jakarta.json.JsonValue;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.annotation.JsonbDateFormat;
import jakarta.json.bind.annotation.JsonbNumberFormat;
import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.annotation.JsonbTransient;
import jakarta.json.bind.annotation.JsonbTypeAdapter;
import jakarta.json.bind.annotation.JsonbTypeDeserializer;
import jakarta.json.bind.annotation.JsonbTypeSerializer;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Runtime JSON-B property model exposed as Micronaut read/write bean property
 * interfaces.
 * <p>
 * The implementation keeps only the access patterns required by the JSON-B
 * reflection provider: field/getter reads, field/setter writes, and synthesized
 * Serde metadata for JSON-B annotations. All reflective members are made
 * accessible at the point of use to support package-private TCK model classes.
 *
 * @param <T> The bean type
 */
@SuppressWarnings({"java:S3776", "java:S1192"})
final class JsonbRuntimeProperty<T> implements BeanProperty<T, Object>, UnsafeBeanReadProperty<T, Object>, UnsafeBeanWriteProperty<T, Object> {
    final int index;
    @Nullable Field field;
    @Nullable Method getter;
    @Nullable Method setter;
    private int order;
    private final JsonbRuntimeBeanIntrospection<T> introspection;
    private final String implicitName;
    private final @Nullable Object namingStrategy;
    private @Nullable AnnotationMetadata readAnnotationMetadata;
    private @Nullable AnnotationMetadata writeAnnotationMetadata;
    private @Nullable Argument<Object> readArgument;
    private @Nullable Argument<Object> writeArgument;

    /**
     * Creates a mutable property model while the owning runtime introspection is
     * discovering fields and accessors. The constructor records the implicit Java
     * property name; JSON-B naming and annotation overrides are resolved lazily
     * because read and write members can be discovered in different passes.
     *
     * @param introspection The owning runtime introspection
     * @param implicitName The JavaBean property name before JSON-B translation
     * @param namingStrategy The effective JSON-B naming strategy
     * @param index The discovery index used as the initial order
     */
    JsonbRuntimeProperty(JsonbRuntimeBeanIntrospection<T> introspection, String implicitName, @Nullable Object namingStrategy, int index) {
        this.introspection = introspection;
        this.implicitName = implicitName;
        this.namingStrategy = namingStrategy;
        this.index = index;
        this.order = index;
    }

    @Override
    public BeanIntrospection<T> getDeclaringBean() {
        return introspection;
    }

    @Override
    public String getName() {
        return resolvePropertyName();
    }

    @Override
    public Object get(T bean) {
        return getUnsafe(bean);
    }

    @Override
    public Object getUnsafe(T bean) {
        AccessibleObject member = readMember();
        if (member == null) {
            throw new JsonbException("Cannot read JSON-B property " + implicitName);
        }
        try {
            if (member instanceof Method method) {
                return method.invoke(bean);
            }
            return ((Field) member).get(bean);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new JsonbException("Cannot access JSON-B property " + implicitName, e);
        }
    }

    @Override
    public T withValue(T bean, @Nullable Object value) {
        return withValueUnsafe(bean, value);
    }

    @Override
    public T withValueUnsafe(T bean, @Nullable Object value) {
        setUnsafe(bean, value);
        return bean;
    }

    @Override
    public void set(T bean, @Nullable Object value) {
        setUnsafe(bean, value);
    }

    @Override
    public void setUnsafe(T bean, @Nullable Object value) {
        AccessibleObject member = writeMember();
        if (member == null) {
            throw new JsonbException("Cannot write JSON-B property " + implicitName);
        }
        try {
            if (member instanceof Method method) {
                method.invoke(bean, value);
            } else {
                ((Field) member).set(bean, value);
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new JsonbException("Cannot set JSON-B property " + implicitName, e);
        }
    }

    @Override
    public Class<Object> getType() {
        return asArgument().getType();
    }

    @Override
    public Argument<Object> asArgument() {
        return argument(true);
    }

    private Argument<Object> argument(boolean serialization) {
        if (serialization) {
            Argument<Object> resolved = readArgument;
            if (resolved == null) {
                resolved = resolveArgument(true);
                readArgument = resolved;
            }
            return resolved;
        }
        Argument<Object> resolved = writeArgument;
        if (resolved == null) {
            resolved = resolveArgument(false);
            writeArgument = resolved;
        }
        return resolved;
    }

    @SuppressWarnings("unchecked")
    private Argument<Object> resolveArgument(boolean serialization) {
        AccessibleObject member = serialization ? readMember() : writeMember();
        Type genericType;
        Class<?> rawType;
        if (member instanceof Method method) {
            if (serialization) {
                genericType = method.getGenericReturnType();
                rawType = method.getReturnType();
            } else {
                genericType = method.getGenericParameterTypes()[0];
                rawType = method.getParameterTypes()[0];
            }
        } else if (member instanceof Field f) {
            genericType = f.getGenericType();
            rawType = f.getType();
        } else {
            genericType = Object.class;
            rawType = Object.class;
        }
        AnnotationMetadata metadata = annotationMetadata(serialization);
        Argument<Object> resolved = ((Argument<Object>) MicronautJsonbProvider.MicronautJsonb.argument(genericType))
            .withName(implicitName)
            .withAnnotationMetadata(metadata);
        if (resolved.getType() == Object.class && rawType != Object.class) {
            resolved = (Argument<Object>) Argument.of(rawType, implicitName, metadata);
        }
        resolved = (Argument<Object>) customizeArgument(resolved);
        return resolved;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata(true);
    }

    private AnnotationMetadata annotationMetadata(boolean serialization) {
        if (serialization) {
            AnnotationMetadata resolved = readAnnotationMetadata;
            if (resolved == null) {
                resolved = propertyMetadata(true);
                readAnnotationMetadata = resolved;
            }
            return resolved;
        }
        AnnotationMetadata resolved = writeAnnotationMetadata;
        if (resolved == null) {
            resolved = propertyMetadata(false);
            writeAnnotationMetadata = resolved;
        }
        return resolved;
    }

    @Override
    public boolean isReadOnly() {
        return writeMember() == null;
    }

    @Override
    public boolean isWriteOnly() {
        return readMember() == null;
    }

    /**
     * Returns the reflective member used for serialization.
     *
     * @return The accessible read member, or {@code null} for write-only properties
     */
    @Nullable AccessibleObject readMember() {
        return accessible(getter != null ? getter : field);
    }

    /**
     * Returns the reflective member used for deserialization.
     *
     * @return The accessible write member, or {@code null} for read-only properties
     */
    @Nullable AccessibleObject writeMember() {
        return accessible(setter != null ? setter : field);
    }

    private static @Nullable AccessibleObject accessible(@Nullable AccessibleObject member) {
        if (member != null) {
            member.setAccessible(true);
        }
        return member;
    }

    private String resolvePropertyName() {
        JsonbProperty property = annotation(JsonbProperty.class, readMember(), writeMember(), field);
        if (property != null && !property.value().isEmpty()) {
            return property.value();
        }
        return JsonbReflectionUtil.translateName(implicitName, namingStrategy);
    }

    /**
     * Returns the generic type exposed to serialization.
     *
     * @return The serialization type, or {@code null} when the property has no readable member
     */
    @Nullable Type serializationType() {
        AccessibleObject member = readMember();
        if (member instanceof Method method) {
            return method.getGenericReturnType();
        }
        if (member instanceof Field f) {
            return f.getGenericType();
        }
        return null;
    }

    /**
     * Returns the generic type accepted during deserialization.
     *
     * @return The deserialization type, or {@code null} when the property has no writable member
     */
    @Nullable Type deserializationType() {
        AccessibleObject member = writeMember();
        if (member instanceof Method method) {
            return method.getGenericParameterTypes()[0];
        }
        if (member instanceof Field f) {
            return f.getGenericType();
        }
        return null;
    }

    /**
     * Returns the JavaBean property name before JSON-B naming overrides.
     *
     * @return The implicit property name
     */
    String implicitName() {
        return implicitName;
    }

    /**
     * Returns the discovered getter.
     *
     * @return The getter, if any
     */
    @Nullable Method getter() {
        return getter;
    }

    /**
     * Returns the discovered setter.
     *
     * @return The setter, if any
     */
    @Nullable Method setter() {
        return setter;
    }

    /**
     * Returns the declaring class of the active read/write member.
     *
     * @return The declaring class, falling back to the bean type for
     * synthetic runtime-only properties
     */
    Class<?> declaringClass() {
        AccessibleObject member = readMember();
        if (member == null) {
            member = writeMember();
        }
        if (member instanceof Method method) {
            return method.getDeclaringClass();
        }
        if (member instanceof Field f) {
            return f.getDeclaringClass();
        }
        return introspection.getBeanType();
    }

    /**
     * Updates the effective property order after the owning introspection has
     * sorted all properties. Cached arguments and metadata are cleared because
     * the order is encoded into Serde metadata.
     *
     * @param order The effective serialization order
     */
    void order(int order) {
        this.order = order;
        this.readAnnotationMetadata = null;
        this.writeAnnotationMetadata = null;
        this.readArgument = null;
        this.writeArgument = null;
    }

    /**
     * Returns whether this property is JSON-B transient.
     *
     * @return Whether Java {@code transient} or {@link JsonbTransient} applies
     */
    boolean isJsonbTransient() {
        return (field != null && Modifier.isTransient(field.getModifiers()))
            || hasJsonbTransient();
    }

    /**
     * Returns whether any backing member declares {@link JsonbTransient}.
     *
     * @return Whether a backing member is annotated as JSON-B transient
     */
    boolean hasJsonbTransient() {
        return (field != null && field.isAnnotationPresent(JsonbTransient.class))
            || (getter != null && getter.isAnnotationPresent(JsonbTransient.class))
            || (setter != null && setter.isAnnotationPresent(JsonbTransient.class));
    }

    /**
     * Returns whether the property declares any JSON-B customization annotation.
     *
     * @return Whether a customization annotation is present
     */
    boolean hasJsonbCustomization() {
        return hasJsonbCustomization(field)
            || hasJsonbCustomization(getter)
            || hasJsonbCustomization(setter);
    }

    /**
     * Returns whether the property declares fallback-only customization.
     *
     * @return Whether generated Serde metadata cannot represent a customization
     */
    boolean hasFallbackCustomization() {
        return hasFallbackCustomization(field)
            || hasFallbackCustomization(getter)
            || hasFallbackCustomization(setter);
    }

    /**
     * Returns the effective serialized JSON property name.
     *
     * @return The serialized name, or {@code null} for write-only properties
     */
    @Nullable String serializationName() {
        if (readMember() == null) {
            return null;
        }
        String name = annotationPropertyName(getter);
        if (name == null) {
            name = annotationPropertyName(field);
        }
        return name == null ? JsonbReflectionUtil.translateName(implicitName, namingStrategy) : name;
    }

    /**
     * Returns the effective deserialized JSON property name.
     *
     * @return The deserialized name, or {@code null} for read-only properties
     */
    @Nullable String deserializationName() {
        if (writeMember() == null) {
            return null;
        }
        String name = annotationPropertyName(setter);
        if (name == null) {
            name = annotationPropertyName(field);
        }
        return name == null ? JsonbReflectionUtil.translateName(implicitName, namingStrategy) : name;
    }

    /**
     * Creates a read-only BeanProperty view.
     *
     * @return A read-only view backed by this runtime property
     */
    BeanReadProperty<T, Object> asReadProperty() {
        return new ReadView();
    }

    /**
     * Creates a write-only BeanProperty view.
     *
     * @return A write-only view backed by this runtime property
     */
    BeanWriteProperty<T, Object> asWriteProperty() {
        return new WriteView();
    }

    private AnnotationMetadata propertyMetadata(boolean serialization) {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        metadata.addAnnotation(Order.class.getName(), Map.of(AnnotationMetadata.VALUE_MEMBER, order));
        String propertyName = serialization ? serializationName() : deserializationName();
        metadata.addAnnotation(SerdeConfig.class.getName(), Map.of(SerdeConfig.PROPERTY, propertyName == null ? resolvePropertyName() : propertyName));
        AccessibleObject primaryMember = serialization ? readMember() : writeMember();
        AccessibleObject secondaryMember = serialization ? writeMember() : readMember();
        if (annotation(JsonbTransient.class, primaryMember, field, secondaryMember) != null) {
            metadata.addAnnotation(SerdeConfig.class.getName(), Map.of(SerdeConfig.IGNORED, true));
        }
        JsonbProperty property = annotation(JsonbProperty.class, primaryMember, field, secondaryMember);
        if (property != null && property.nillable()) {
            metadata.addAnnotation(SerdeConfig.class.getName(), Map.of(SerdeConfig.INCLUDE, SerdeConfig.SerInclude.ALWAYS.name()));
        }
        JsonbNumberFormat numberFormat = JsonbReflectionUtil.numberFormat(primaryMember instanceof Method method ? method : null, field, introspection.getBeanType());
        if (numberFormat != null) {
            Map<CharSequence, Object> values = new LinkedHashMap<>();
            if (!numberFormat.value().isEmpty()) {
                values.put(SerdeConfig.PATTERN, numberFormat.value());
                if ("##default".equals(numberFormat.locale())) {
                    values.put(SerdeConfig.LOCALE, Locale.US.toLanguageTag());
                }
            }
            if (!"##default".equals(numberFormat.locale())) {
                values.put(SerdeConfig.LOCALE, numberFormat.locale());
            }
            metadata.addAnnotation(SerdeConfig.class.getName(), values);
        }
        JsonbDateFormat dateFormat = JsonbReflectionUtil.dateFormat(primaryMember instanceof Method method ? method : null, field, introspection.getBeanType());
        if (dateFormat != null && !"##time-in-millis".equals(dateFormat.value())) {
            String pattern = "##default".equals(dateFormat.value()) ? DateTimeFormatter.RFC_1123_DATE_TIME.toString() : dateFormat.value();
            Map<CharSequence, Object> values = new LinkedHashMap<>();
            values.put(SerdeConfig.PATTERN, pattern);
            if (!"##default".equals(dateFormat.locale())) {
                values.put(SerdeConfig.LOCALE, dateFormat.locale());
            }
            metadata.addAnnotation(SerdeConfig.class.getName(), values);
        }
        JsonbTypeAdapter adapter = annotation(JsonbTypeAdapter.class, primaryMember, field, secondaryMember);
        if (adapter != null) {
            metadata.addAnnotation(SerdeConfig.class.getName(), Map.of(
                SerdeConfig.SERIALIZER_CLASS, new AnnotationClassValue<>(JsonbTypeAdapterSerde.class),
                SerdeConfig.DESERIALIZER_CLASS, new AnnotationClassValue<>(JsonbTypeAdapterSerde.class)
            ));
            metadata.addAnnotation(JsonbSerdeConfig.class.getName(), Map.of("adapter", new AnnotationClassValue<>(adapter.value())));
        }
        JsonbTypeSerializer serializer = annotation(JsonbTypeSerializer.class, primaryMember, field, secondaryMember);
        if (serializer != null) {
            metadata.addAnnotation(SerdeConfig.class.getName(), Map.of(SerdeConfig.SERIALIZER_CLASS, new AnnotationClassValue<>(JsonbTypeSerializerBridge.class)));
            metadata.addAnnotation(JsonbSerdeConfig.class.getName(), Map.of("serializer", new AnnotationClassValue<>(serializer.value())));
        }
        JsonbTypeDeserializer deserializer = annotation(JsonbTypeDeserializer.class, primaryMember, field, secondaryMember);
        if (deserializer != null) {
            metadata.addAnnotation(SerdeConfig.class.getName(), Map.of(SerdeConfig.DESERIALIZER_CLASS, new AnnotationClassValue<>(JsonbTypeDeserializerBridge.class)));
            metadata.addAnnotation(JsonbSerdeConfig.class.getName(), Map.of("deserializer", new AnnotationClassValue<>(deserializer.value())));
        }
        Type configuredType = serialization ? serializationType() : deserializationType();
        if (configuredType == null) {
            configuredType = serialization ? deserializationType() : serializationType();
        }
        if (configuredType != null) {
            Class<?> erasedType = erasedType(configuredType);
            if (erasedType != null && JsonValue.class.isAssignableFrom(erasedType)) {
                metadata.addAnnotation(SerdeConfig.class.getName(), Map.of(
                    SerdeConfig.SERIALIZER_CLASS, new AnnotationClassValue<>(JsonpValueSerde.class),
                    SerdeConfig.DESERIALIZER_CLASS, new AnnotationClassValue<>(JsonpValueSerde.class)
                ));
            }
            if (erasedType != null && GregorianCalendar.class.isAssignableFrom(erasedType)) {
                metadata.addAnnotation(SerdeConfig.class.getName(), Map.of(
                    SerdeConfig.SERIALIZER_CLASS, new AnnotationClassValue<>(JsonbGregorianCalendarSerde.class),
                    SerdeConfig.DESERIALIZER_CLASS, new AnnotationClassValue<>(JsonbGregorianCalendarSerde.class)
                ));
            } else if (erasedType != null && Calendar.class.isAssignableFrom(erasedType)) {
                metadata.addAnnotation(SerdeConfig.class.getName(), Map.of(
                    SerdeConfig.SERIALIZER_CLASS, new AnnotationClassValue<>(JsonbCalendarSerde.class),
                    SerdeConfig.DESERIALIZER_CLASS, new AnnotationClassValue<>(JsonbCalendarSerde.class)
                ));
            } else if (erasedType == OffsetTime.class) {
                metadata.addAnnotation(SerdeConfig.class.getName(), Map.of(
                    SerdeConfig.SERIALIZER_CLASS, new AnnotationClassValue<>(JsonbOffsetTimeSerde.class),
                    SerdeConfig.DESERIALIZER_CLASS, new AnnotationClassValue<>(JsonbOffsetTimeSerde.class)
                ));
            } else if (erasedType == ZoneOffset.class) {
                metadata.addAnnotation(SerdeConfig.class.getName(), Map.of(
                    SerdeConfig.SERIALIZER_CLASS, new AnnotationClassValue<>(JsonbZoneOffsetSerde.class),
                    SerdeConfig.DESERIALIZER_CLASS, new AnnotationClassValue<>(JsonbZoneOffsetSerde.class)
                ));
            }
            introspection.customizations().applySerdeMetadata(metadata, configuredType);
        }
        return metadata;
    }

    private static @Nullable Class<?> erasedType(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterizedType && parameterizedType.getRawType() instanceof Class<?> clazz) {
            return clazz;
        }
        return null;
    }

    private Argument<?> customizeArgument(Argument<?> source) {
        Argument<?>[] typeParameters = source.getTypeParameters();
        Argument<?>[] customizedTypeParameters = typeParameters;
        for (int i = 0; i < typeParameters.length; i++) {
            Argument<?> customized = customizeArgument(typeParameters[i]);
            if (customized != typeParameters[i]) {
                if (customizedTypeParameters == typeParameters) {
                    customizedTypeParameters = typeParameters.clone();
                }
                customizedTypeParameters[i] = customized;
            }
        }
        MutableAnnotationMetadata customizationMetadata = new MutableAnnotationMetadata();
        introspection.customizations().applySerdeMetadata(customizationMetadata, source.asType());
        AnnotationMetadata metadata = source.getAnnotationMetadata();
        if (!customizationMetadata.isEmpty()) {
            metadata = metadata.isEmpty()
                ? customizationMetadata
                : new io.micronaut.inject.annotation.AnnotationMetadataHierarchy(metadata, customizationMetadata);
        }
        if (metadata == source.getAnnotationMetadata() && customizedTypeParameters == typeParameters) {
            return source;
        }
        return Argument.of(source.getType(), source.getName(), metadata, customizedTypeParameters);
    }

    private static <A extends Annotation> @Nullable A annotation(Class<A> annotationType, @Nullable AccessibleObject first, @Nullable AccessibleObject second, @Nullable AccessibleObject third) {
        A annotation = first == null ? null : first.getAnnotation(annotationType);
        if (annotation != null) {
            return annotation;
        }
        annotation = second == null ? null : second.getAnnotation(annotationType);
        if (annotation != null) {
            return annotation;
        }
        return third == null ? null : third.getAnnotation(annotationType);
    }

    private static @Nullable String annotationPropertyName(@Nullable AccessibleObject member) {
        if (member == null) {
            return null;
        }
        JsonbProperty property = member.getAnnotation(JsonbProperty.class);
        return property == null || property.value().isEmpty() ? null : property.value();
    }

    private static boolean hasJsonbCustomization(@Nullable AccessibleObject member) {
        if (member == null) {
            return false;
        }
        for (Annotation annotation : member.getAnnotations()) {
            Class<? extends Annotation> annotationType = annotation.annotationType();
            Package annotationPackage = annotationType.getPackage();
            if (annotationPackage != null
                && "jakarta.json.bind.annotation".equals(annotationPackage.getName())
                && annotationType != JsonbTransient.class) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasFallbackCustomization(@Nullable AccessibleObject member) {
        return member != null
            && (member.isAnnotationPresent(JsonbTypeAdapter.class)
            || member.isAnnotationPresent(JsonbTypeSerializer.class)
            || member.isAnnotationPresent(JsonbTypeDeserializer.class));
    }

    private final class ReadView implements BeanReadProperty<T, Object>, UnsafeBeanReadProperty<T, Object> {
        @Override
        public BeanIntrospection<T> getDeclaringBean() {
            return introspection;
        }

        @Override
        public String getName() {
            String name = serializationName();
            return name == null ? resolvePropertyName() : name;
        }

        @Override
        public Object get(T bean) {
            return JsonbRuntimeProperty.this.get(bean);
        }

        @Override
        public Object getUnsafe(T bean) {
            return JsonbRuntimeProperty.this.getUnsafe(bean);
        }

        @Override
        public Class<Object> getType() {
            return asArgument().getType();
        }

        @Override
        public Argument<Object> asArgument() {
            return argument(true);
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return annotationMetadata(true);
        }
    }

    private final class WriteView implements BeanWriteProperty<T, Object>, UnsafeBeanWriteProperty<T, Object> {
        @Override
        public BeanIntrospection<T> getDeclaringBean() {
            return introspection;
        }

        @Override
        public String getName() {
            String name = deserializationName();
            return name == null ? resolvePropertyName() : name;
        }

        @Override
        public T withValue(T bean, @Nullable Object value) {
            return JsonbRuntimeProperty.this.withValue(bean, value);
        }

        @Override
        public T withValueUnsafe(T bean, @Nullable Object value) {
            return JsonbRuntimeProperty.this.withValueUnsafe(bean, value);
        }

        @Override
        public void set(T bean, @Nullable Object value) {
            JsonbRuntimeProperty.this.set(bean, value);
        }

        @Override
        public void setUnsafe(T bean, @Nullable Object value) {
            JsonbRuntimeProperty.this.setUnsafe(bean, value);
        }

        @Override
        public Class<Object> getType() {
            return asArgument().getType();
        }

        @Override
        public Argument<Object> asArgument() {
            return argument(false);
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return annotationMetadata(false);
        }
    }
}
