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
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.beans.BeanConstructor;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.BeanReadProperty;
import io.micronaut.core.beans.BeanWriteProperty;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.serde.config.annotation.SerdeConfig;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.annotation.JsonbPropertyOrder;
import jakarta.json.bind.annotation.JsonbSubtype;
import jakarta.json.bind.annotation.JsonbTypeInfo;
import jakarta.json.bind.annotation.JsonbVisibility;
import jakarta.json.bind.config.PropertyOrderStrategy;
import jakarta.json.bind.config.PropertyVisibilityStrategy;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reflection-backed JSON-B {@link BeanIntrospection} implementation used only by
 * the JSON-B reflection provider.
 * <p>
 * This is deliberately a subset of the full Micronaut introspection contract:
 * it exposes the property, constructor, and Serde annotation metadata needed by
 * Micronaut Serialization's {@code SerBean}/{@code DeserBean} path and the
 * Jakarta JSON-B TCK. It is also the cached runtime model used for write-path
 * preflight decisions, so those decisions stay aligned with the metadata shape
 * produced for serialization.
 *
 * @param <T> The bean type
 */
@SuppressWarnings("java:S3776")
final class JsonbRuntimeBeanIntrospection<T> implements BeanIntrospection<T> {
    private final Class<T> type;
    private final AnnotationMetadata annotationMetadata;
    private final List<JsonbRuntimeProperty<T>> properties;
    private final List<JsonbRuntimeProperty<T>> validationProperties;
    private final List<BeanReadProperty<T, Object>> readProperties;
    private final List<BeanWriteProperty<T, Object>> writeProperties;
    private final JsonbRuntimeBeanConstructor<T> constructor;
    private final JsonbRuntimeCustomizations customizations;
    private final @Nullable Class<? extends PropertyVisibilityStrategy> visibilityStrategyType;
    private final boolean hasTypeInfo;
    private final boolean requiresFallback;
    private final boolean canWriteGeneratedDirectly;

    /**
     * Builds the runtime JSON-B model for one class and mapper configuration.
     * The model intentionally computes both visible runtime properties and a
     * wider validation property set so JSON-B validation can still detect
     * duplicate names or invalid transient/customization combinations on
     * members hidden from serialization.
     *
     * @param type The bean type
     * @param namingStrategy The effective JSON-B naming strategy
     * @param propertyOrderStrategy The effective JSON-B property ordering strategy
     * @param visibilityStrategy The effective JSON-B visibility strategy
     * @param customizations Configured JSON-B adapters, serializers, and deserializers
     */
    JsonbRuntimeBeanIntrospection(Class<T> type,
                                  @Nullable Object namingStrategy,
                                  String propertyOrderStrategy,
                                  @Nullable PropertyVisibilityStrategy visibilityStrategy,
                                  JsonbRuntimeCustomizations customizations) {
        this.type = type;
        this.customizations = customizations;
        this.visibilityStrategyType = visibilityStrategyType(type);
        Map<String, Object> typeInfoProperties = JsonbTypeInfoSupport.typeInfoProperties(type);
        this.hasTypeInfo = !typeInfoProperties.isEmpty() || JsonbTypeInfoSupport.hasTypeInfo(type);
        Map<String, JsonbRuntimeProperty<T>> allModels = new LinkedHashMap<>();
        int[] allPropertyIndex = {0};
        Set<String> hiddenAccessorPropertyNames = new HashSet<>();
        for (Field field : JsonbReflectionUtil.fields(type)) {
            if (field.isSynthetic() || Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            allModels.computeIfAbsent(field.getName(), n -> new JsonbRuntimeProperty<>(this, n, namingStrategy, allPropertyIndex[0]++)).field = field;
        }
        for (Method method : JsonbReflectionUtil.methods(type)) {
            if (JsonbReflectionUtil.isGetterName(method) && method.getParameterCount() == 0 && !method.isSynthetic() && !method.isBridge() && method.getDeclaringClass() != Object.class) {
                String name = JsonbReflectionUtil.implicitPropertyName(method);
                if (visibilityStrategy == null && !JsonbReflectionUtil.isVisible(method, null)) {
                    hiddenAccessorPropertyNames.add(name);
                }
                allModels.computeIfAbsent(name, n -> new JsonbRuntimeProperty<>(this, n, namingStrategy, allPropertyIndex[0]++)).getter = method;
            } else if (method.getName().startsWith("set") && method.getName().length() > 3 && method.getParameterCount() == 1 && !method.isSynthetic() && !method.isBridge()) {
                String name = JsonbReflectionUtil.implicitPropertyName(method);
                if (visibilityStrategy == null && !JsonbReflectionUtil.isVisibleSetter(method, null)) {
                    hiddenAccessorPropertyNames.add(name);
                }
                allModels.computeIfAbsent(name, n -> new JsonbRuntimeProperty<>(this, n, namingStrategy, allPropertyIndex[0]++)).setter = method;
            }
        }
        this.validationProperties = List.copyOf(allModels.values());
        Map<String, JsonbRuntimeProperty<T>> models = new LinkedHashMap<>();
        Set<String> transientProperties = JsonbReflectionUtil.transientProperties(type);
        int[] propertyIndex = {0};
        for (Method method : JsonbReflectionUtil.methods(type)) {
            if (JsonbReflectionUtil.isGetter(method) && JsonbReflectionUtil.isVisible(method, visibilityStrategy)) {
                String name = JsonbReflectionUtil.implicitPropertyName(method);
                if (!transientProperties.contains(name) && !JsonbReflectionUtil.isStaticBackedAccessor(type, name)) {
                    models.computeIfAbsent(name, n -> new JsonbRuntimeProperty<>(this, n, namingStrategy, propertyIndex[0]++)).getter = method;
                }
            } else if (JsonbReflectionUtil.isSetter(method) && JsonbReflectionUtil.isVisibleSetter(method, visibilityStrategy)) {
                String name = JsonbReflectionUtil.implicitPropertyName(method);
                if (!transientProperties.contains(name) && !JsonbReflectionUtil.isStaticBackedAccessor(type, name)) {
                    models.computeIfAbsent(name, n -> new JsonbRuntimeProperty<>(this, n, namingStrategy, propertyIndex[0]++)).setter = method;
                }
            }
        }
        boolean hiddenAccessorBackedField = false;
        for (Field field : JsonbReflectionUtil.fields(type)) {
            if (JsonbReflectionUtil.isFieldProperty(field) && JsonbReflectionUtil.isVisible(field, visibilityStrategy) && !transientProperties.contains(field.getName())) {
                if (visibilityStrategy == null && hiddenAccessorPropertyNames.contains(field.getName())) {
                    hiddenAccessorBackedField = true;
                    continue;
                }
                models.computeIfAbsent(field.getName(), n -> new JsonbRuntimeProperty<>(this, n, namingStrategy, propertyIndex[0]++)).field = field;
            }
        }
        List<JsonbRuntimeProperty<T>> resolvedProperties = new ArrayList<>(models.values());
        resolvedProperties.removeIf(property -> property.readMember() == null && property.writeMember() == null);
        resolvedProperties.sort(propertyComparator(type, propertyOrderStrategy));
        for (int i = 0; i < resolvedProperties.size(); i++) {
            resolvedProperties.get(i).order(i);
        }
        this.properties = List.copyOf(resolvedProperties);
        this.annotationMetadata = typeMetadata(type, propertyOrderStrategy, this.properties);
        this.readProperties = this.properties.stream()
            .filter(property -> property.readMember() != null)
            .map(JsonbRuntimeProperty::asReadProperty)
            .toList();
        this.writeProperties = this.properties.stream()
            .filter(property -> property.writeMember() != null)
            .map(JsonbRuntimeProperty::asWriteProperty)
            .toList();
        this.constructor = JsonbRuntimeBeanConstructor.of(type);
        this.requiresFallback = requiresAnonymousFallback(type)
            || validationProperties.stream().anyMatch(JsonbRuntimeProperty::isJsonbTransient)
            || validationProperties.stream().anyMatch(JsonbRuntimeProperty::hasFallbackCustomization)
            || hasTypeOrPackageFormat(type)
            || hasAsymmetricAccessorNames()
            || hasAsymmetricAccessorFormats()
            || hiddenAccessorBackedField
            || hasStaticBackedAccessor();
        boolean hasGeneratedWriteFallbackProperty = hasGeneratedWriteFallbackProperty();
        this.canWriteGeneratedDirectly = MicronautJsonbReflectionProvider.MicronautJsonb.canResolveGeneratedSerde(type)
            && !PropertyOrderStrategy.REVERSE.equals(propertyOrderStrategy)
            && !requiresFallback
            && !hasGeneratedWriteFallbackProperty;
    }

    /**
     * Validates the parts of the runtime model that can make JSON-B
     * deserialization illegal before the Serde deserializer mutates a bean.
     */
    void validateReadModel() {
        validateObjectModel();
        JsonbReflectionUtil.validateCreatorModel(type);
        if (!hasTypeInfo) {
            JsonbReflectionUtil.validateDefaultConstructorAccess(type);
        }
    }

    /**
     * Validates the parts of the runtime model that can make JSON-B
     * serialization illegal before any bytes are written to the caller's target.
     */
    void validateWriteModel() {
        validateObjectModel();
    }

    private void validateObjectModel() {
        for (JsonbRuntimeProperty<T> property : validationProperties) {
            if (property.hasJsonbTransient() && property.hasJsonbCustomization()) {
                throw new JsonbException("JsonbTransient cannot be combined with other JSON-B customization annotations on property " + property.implicitName());
            }
        }
        validateNoDuplicateNames(true);
        validateNoDuplicateNames(false);
    }

    private void validateNoDuplicateNames(boolean serialization) {
        Map<String, String> names = new LinkedHashMap<>();
        for (JsonbRuntimeProperty<T> property : validationProperties) {
            if (property.isJsonbTransient()) {
                continue;
            }
            String name = serialization ? property.serializationName() : property.deserializationName();
            if (name == null) {
                continue;
            }
            String previous = names.putIfAbsent(name, property.implicitName());
            if (previous != null && !previous.equals(property.implicitName())) {
                throw new JsonbException("Duplicate JSON-B property name: " + name);
            }
        }
    }

    boolean requiresFallback() {
        return requiresFallback;
    }

    /**
     * Returns the {@link JsonbVisibility} strategy declared on the type or one
     * of its packages.
     *
     * @return The visibility strategy type, if present
     */
    @Nullable Class<? extends PropertyVisibilityStrategy> visibilityStrategyType() {
        return visibilityStrategyType;
    }

    /**
     * Returns the JSON property names that may be read into this model.
     *
     * @return The property names used by fallback unknown-property validation
     * before Serde deserialization
     */
    Set<String> deserializablePropertyNames() {
        Set<String> names = new HashSet<>();
        for (JsonbRuntimeProperty<T> property : validationProperties) {
            if (!property.isJsonbTransient()) {
                String n = property.deserializationName();
                if (n != null) {
                    names.add(n);
                }
            }
        }
        return names;
    }

    /**
     * Returns the visible JSON-B runtime properties.
     *
     * @return The runtime properties in effective serialization order
     */
    List<JsonbRuntimeProperty<T>> runtimeProperties() {
        return properties;
    }

    /**
     * Returns the mapper-level JSON-B customizations.
     *
     * @return The customizations applied to properties of this model
     */
    JsonbRuntimeCustomizations customizations() {
        return customizations;
    }

    /**
     * Tests whether the generated Serde serializer can be used after the
     * reflection provider has performed JSON-B validation. This must remain
     * conservative: any JSON-B rule not represented in generated metadata should
     * force fallback.
     *
     * @param argument The requested runtime argument
     * @return Whether generated serialization is safe for this model
     */
    boolean canWriteGeneratedDirectly(Argument<?> argument) {
        return argument.getType() != Object.class
            && argument.getType().isAssignableFrom(type)
            && canWriteGeneratedDirectly
            && !MicronautJsonbReflectionProvider.MicronautJsonb.requiresGenericNumberFallback(argument);
    }

    private boolean hasGeneratedWriteFallbackProperty() {
        if (type.isAnonymousClass() || type.isLocalClass()) {
            return true;
        }
        for (JsonbRuntimeProperty<T> property : properties) {
            Type propertyType = property.serializationType();
            if (propertyType != null && requiresGeneratedWriteFallback(propertyType)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAsymmetricAccessorNames() {
        Map<String, String> getterNames = new LinkedHashMap<>();
        for (JsonbRuntimeProperty<T> property : validationProperties) {
            if (property.getter() != null) {
                String name = property.serializationName();
                if (name != null) {
                    getterNames.put(property.implicitName(), name);
                }
            }
        }
        for (JsonbRuntimeProperty<T> property : validationProperties) {
            if (property.setter() != null) {
                String setterName = property.deserializationName();
                String getterName = getterNames.get(property.implicitName());
                if (getterName != null && setterName != null && !getterName.equals(setterName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasAsymmetricAccessorFormats() {
        Map<String, String> getterFormats = new LinkedHashMap<>();
        for (JsonbRuntimeProperty<T> property : validationProperties) {
            Method getter = property.getter();
            if (getter != null) {
                String format = formatSignature(getter);
                if (format != null) {
                    getterFormats.put(property.implicitName(), format);
                }
            }
        }
        for (JsonbRuntimeProperty<T> property : validationProperties) {
            Method setter = property.setter();
            if (setter != null) {
                String setterFormat = formatSignature(setter);
                String getterFormat = getterFormats.get(property.implicitName());
                if (getterFormat != null && setterFormat != null && !getterFormat.equals(setterFormat)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasStaticBackedAccessor() {
        for (JsonbRuntimeProperty<T> property : validationProperties) {
            if ((property.getter() != null || property.setter() != null) && JsonbReflectionUtil.isStaticBackedAccessor(type, property.implicitName())) {
                return true;
            }
        }
        return false;
    }

    private static @Nullable String formatSignature(Method method) {
        jakarta.json.bind.annotation.JsonbNumberFormat numberFormat = method.getAnnotation(jakarta.json.bind.annotation.JsonbNumberFormat.class);
        if (numberFormat != null) {
            return "number:" + numberFormat.value() + ':' + numberFormat.locale();
        }
        jakarta.json.bind.annotation.JsonbDateFormat dateFormat = method.getAnnotation(jakarta.json.bind.annotation.JsonbDateFormat.class);
        if (dateFormat != null) {
            return "date:" + dateFormat.value() + ':' + dateFormat.locale();
        }
        return null;
    }

    private static boolean requiresAnonymousFallback(Class<?> type) {
        if (!type.isAnonymousClass() && !type.isLocalClass()) {
            return false;
        }
        Class<?> superclass = type.getSuperclass();
        return superclass != null && superclass.getSuperclass() != null && superclass.getSuperclass() != Object.class;
    }

    private static boolean hasTypeOrPackageFormat(Class<?> type) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            if (current.isAnnotationPresent(jakarta.json.bind.annotation.JsonbNumberFormat.class) || current.isAnnotationPresent(jakarta.json.bind.annotation.JsonbDateFormat.class)) {
                return true;
            }
            current = current.getSuperclass();
        }
        current = type;
        while (current != null && current != Object.class) {
            Package typePackage = current.getPackage();
            if (typePackage != null
                && (typePackage.isAnnotationPresent(jakarta.json.bind.annotation.JsonbNumberFormat.class)
                || typePackage.isAnnotationPresent(jakarta.json.bind.annotation.JsonbDateFormat.class))) {
                return true;
            }
            current = current.getSuperclass();
        }
        return false;
    }

    /**
     * Finds the effective JSON-B visibility strategy class declared on a type
     * hierarchy or package hierarchy.
     *
     * @param type The type to inspect
     * @return The configured visibility strategy type, if any
     */
    static @Nullable Class<? extends PropertyVisibilityStrategy> visibilityStrategyType(Class<?> type) {
        Class<?> current = type;
        while (current != Object.class && current != null) {
            JsonbVisibility annotation = current.getAnnotation(JsonbVisibility.class);
            if (annotation != null) {
                return annotation.value();
            }
            if (current.getPackage() != null) {
                annotation = current.getPackage().getAnnotation(JsonbVisibility.class);
                if (annotation != null) {
                    return annotation.value();
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static boolean requiresGeneratedWriteFallback(Type type) {
        switch (type) {
            case Class<?> clazz -> {
                if (clazz.isArray()) {
                    return requiresGeneratedWriteFallback(clazz.getComponentType());
                }
                return requiresJsonbScalarFallback(clazz);
            }
            case ParameterizedType parameterizedType -> {
                if (parameterizedType.getRawType() == Optional.class) {
                    return true;
                }
                for (Type argument : parameterizedType.getActualTypeArguments()) {
                    if (requiresGeneratedWriteFallback(argument)) {
                        return true;
                    }
                }
                return false;
            }
            case GenericArrayType genericArrayType -> {
                return requiresGeneratedWriteFallback(genericArrayType.getGenericComponentType());
            }
            default -> {
            }
        }
        return false;
    }

    private static boolean requiresJsonbScalarFallback(Class<?> type) {
        return JsonbScalarTypes.isJsonDateTimeScalar(type);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Collection<BeanProperty<T, Object>> getBeanProperties() {
        return (Collection) properties;
    }

    @Override
    public List<BeanReadProperty<T, Object>> getBeanReadProperties() {
        return readProperties;
    }

    @Override
    public List<BeanWriteProperty<T, Object>> getBeanWriteProperties() {
        return writeProperties;
    }

    @Override
    public Collection<BeanProperty<T, Object>> getIndexedProperties(Class<? extends Annotation> annotation) {
        return List.of();
    }

    @Override
    public Builder<T> builder() {
        throw new UnsupportedOperationException("Runtime JSON-B introspections do not support builders");
    }

    @Override
    public T instantiate() {
        return constructor.instantiate();
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public T instantiate(boolean strictNullable, Object... arguments) {
        return constructor.instantiate(arguments);
    }

    @Override
    public Argument<?>[] getConstructorArguments() {
        return constructor.getArguments();
    }

    @Override
    public Class<T> getBeanType() {
        return type;
    }

    @Override
    public Optional<BeanProperty<T, Object>> getIndexedProperty(Class<? extends Annotation> annotation, String value) {
        return Optional.empty();
    }

    @Override
    public Optional<BeanProperty<T, Object>> getProperty(String name) {
        for (JsonbRuntimeProperty<T> property : properties) {
            if (property.getName().equals(name)) {
                return Optional.of(property);
            }
        }
        return Optional.empty();
    }

    @Override
    public int propertyIndexOf(String name) {
        for (int i = 0; i < properties.size(); i++) {
            if (properties.get(i).getName().equals(name)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public String[] getPropertyNames() {
        return properties.stream().map(JsonbRuntimeProperty::getName).toArray(String[]::new);
    }

    @Override
    public String[] getReadPropertyNames() {
        return readProperties.stream().map(BeanReadProperty::getName).toArray(String[]::new);
    }

    @Override
    public BeanConstructor<T> getConstructor() {
        return constructor;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    private static Comparator<JsonbRuntimeProperty<?>> propertyComparator(Class<?> type, String propertyOrderStrategy) {
        JsonbPropertyOrder order = JsonbReflectionUtil.propertyOrder(type);
        if (order != null && order.value().length > 0) {
            List<String> names = List.of(order.value());
            return Comparator.comparingInt(property -> {
                int index = names.indexOf(property.getName());
                return index < 0 ? Integer.MAX_VALUE : index;
            });
        }
        if (PropertyOrderStrategy.REVERSE.equals(propertyOrderStrategy)) {
            return Comparator.comparing((JsonbRuntimeProperty<?> property) -> property.getName()).reversed();
        }
        if (PropertyOrderStrategy.LEXICOGRAPHICAL.equals(propertyOrderStrategy)) {
            Map<Class<?>, Integer> hierarchy = hierarchyOrder(type);
            return Comparator
                .comparingInt((JsonbRuntimeProperty<?> property) -> hierarchy.getOrDefault(property.declaringClass(), Integer.MAX_VALUE))
                .thenComparing(JsonbRuntimeProperty::getName);
        }
        return Comparator.comparingInt(property -> property.index);
    }

    private static Map<Class<?>, Integer> hierarchyOrder(Class<?> type) {
        List<Class<?>> hierarchy = JsonbReflectionUtil.resolveHierarchy(type);
        Map<Class<?>, Integer> order = new LinkedHashMap<>();
        for (int i = 0; i < hierarchy.size(); i++) {
            order.put(hierarchy.get(i), i);
        }
        return order;
    }

    private static AnnotationMetadata typeMetadata(Class<?> type,
                                                   String propertyOrderStrategy,
                                                   List<? extends JsonbRuntimeProperty<?>> properties) {
        MutableAnnotationMetadata metadata = new MutableAnnotationMetadata();
        JsonbPropertyOrder propertyOrder = JsonbReflectionUtil.propertyOrder(type);
        if (propertyOrder != null && propertyOrder.value().length > 0) {
            metadata.addAnnotation(SerdeConfig.META_ANNOTATION_PROPERTY_ORDER, Map.of(AnnotationMetadata.VALUE_MEMBER, propertyOrder.value()));
        } else if (PropertyOrderStrategy.LEXICOGRAPHICAL.equals(propertyOrderStrategy) && !properties.isEmpty()) {
            metadata.addAnnotation(SerdeConfig.META_ANNOTATION_PROPERTY_ORDER, Map.of(
                AnnotationMetadata.VALUE_MEMBER,
                properties.stream().map(JsonbRuntimeProperty::getName).toArray(String[]::new)
            ));
        }
        addTypeInfoMetadata(type, metadata);
        return metadata;
    }

    private static void addTypeInfoMetadata(Class<?> type, MutableAnnotationMetadata metadata) {
        List<Class<?>> annotatedTypes = JsonbTypeInfoSupport.annotatedTypeInfoTypes(type);
        if (annotatedTypes.isEmpty()) {
            return;
        }
        JsonbTypeInfoSupport.ensureSingleTypeInfoChain(annotatedTypes, type);
        List<String> typeProperties = new ArrayList<>();
        List<String> typePropertyValues = new ArrayList<>();
        List<String> currentTypeNames = List.of();
        String currentTypeProperty = null;
        for (Class<?> annotatedType : annotatedTypes) {
            JsonbTypeInfo typeInfo = annotatedType.getAnnotation(JsonbTypeInfo.class);
            if (typeInfo == null) {
                continue;
            }
            if (annotatedType == type) {
                List<AnnotationValue<SerdeConfig.SerSubtyped.SerSubtype>> subtypes = new ArrayList<>();
                for (JsonbSubtype subtype : typeInfo.value()) {
                    subtypes.add(AnnotationValue.builder(SerdeConfig.SerSubtyped.SerSubtype.class)
                        .member(AnnotationMetadata.VALUE_MEMBER, new AnnotationClassValue<>(subtype.type()))
                        .member("names", new String[]{subtype.alias()})
                        .build());
                }
                if (!subtypes.isEmpty()) {
                    metadata.addAnnotation(SerdeConfig.SerSubtyped.class.getName(), Map.of(
                        AnnotationMetadata.VALUE_MEMBER, subtypes.toArray(new AnnotationValue[0]),
                        SerdeConfig.SerSubtyped.DISCRIMINATOR_TYPE, SerdeConfig.SerSubtyped.DiscriminatorType.PROPERTY,
                        SerdeConfig.SerSubtyped.DISCRIMINATOR_VALUE, SerdeConfig.SerSubtyped.DiscriminatorValueKind.NAME,
                        SerdeConfig.SerSubtyped.DISCRIMINATOR_PROP, typeInfo.key(),
                        SerdeConfig.SerSubtyped.JSONB_TYPE_INFO, true
                    ));
                }
            }
            List<String> names = new ArrayList<>();
            for (JsonbSubtype subtype : typeInfo.value()) {
                if (subtype.type().isAssignableFrom(type)) {
                    names.add(subtype.alias());
                }
            }
            if (!names.isEmpty()) {
                typeProperties.add(typeInfo.key());
                typePropertyValues.add(names.getFirst());
                currentTypeNames = names;
                currentTypeProperty = typeInfo.key();
            }
        }
        if (currentTypeProperty != null && !currentTypeNames.isEmpty()) {
            Map<CharSequence, Object> values = new LinkedHashMap<>();
            values.put(SerdeConfig.TYPE_NAME, currentTypeNames.getFirst());
            values.put(SerdeConfig.TYPE_NAMES, currentTypeNames.toArray(new String[0]));
            values.put(SerdeConfig.TYPE_PROPERTY, currentTypeProperty);
            if (typeProperties.size() > 1) {
                values.put(SerdeConfig.TYPE_PROPERTIES, typeProperties.toArray(new String[0]));
                values.put(SerdeConfig.TYPE_PROPERTY_VALUES, typePropertyValues.toArray(new String[0]));
                JsonbPropertyOrder jsonbPropertyOrder = JsonbReflectionUtil.propertyOrder(type);
                if (jsonbPropertyOrder == null || jsonbPropertyOrder.value().length == 0) {
                    metadata.addAnnotation(SerdeConfig.META_ANNOTATION_PROPERTY_ORDER, Map.of(
                        AnnotationMetadata.VALUE_MEMBER,
                        jsonbTypeInfoPropertyOrder(type, typeProperties).toArray(new String[0])
                    ));
                }
            }
            metadata.addAnnotation(SerdeConfig.class.getName(), values);
        }
    }

    private static List<String> jsonbTypeInfoPropertyOrder(Class<?> type, List<String> typeProperties) {
        List<String> order = new ArrayList<>(typeProperties);
        List<Class<?>> hierarchy = JsonbReflectionUtil.resolveHierarchy(type);
        for (Class<?> hierarchyType : hierarchy) {
            for (Field field : hierarchyType.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers())) {
                    addIfAbsent(order, field.getName());
                }
            }
            for (Method method : hierarchyType.getDeclaredMethods()) {
                if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0 || method.getReturnType() == Void.TYPE) {
                    continue;
                }
                String methodName = method.getName();
                if (methodName.startsWith("get") && methodName.length() > 3) {
                    addIfAbsent(order, JsonbReflectionUtil.decapitalize(methodName.substring(3)));
                } else if (methodName.startsWith("is") && methodName.length() > 2) {
                    addIfAbsent(order, JsonbReflectionUtil.decapitalize(methodName.substring(2)));
                }
            }
        }
        return order;
    }

    private static void addIfAbsent(List<String> values, String value) {
        if (!values.contains(value)) {
            values.add(value);
        }
    }
}
