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
package io.micronaut.serde.processor.sourcegen.beans;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.PropertyElementQuery;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.processor.sourcegen.SerdeInclusionSourceGen;
import io.micronaut.serde.processor.sourcegen.SerdeSourceGenPropertyOrder;
import io.micronaut.serde.util.SerdePropertyAccess;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves bean shapes eligible for source-generated serdes.
 *
 * <p>Properties are resolved per direction the way the runtime serdes resolve them: a property is
 * written when it is readable and not excluded from serialization, and read when it is writable and
 * not excluded from deserialization. Writable properties excluded from deserialization are skipped
 * silently by the generated deserializer, matching the runtime object deserializer.</p>
 */
public final class BeanSerdeShapeResolver {

    public Optional<BeanSerdeShape> resolve(ClassElement element) {
        if (!isBeanShapeCandidate(element)) {
            return Optional.empty();
        }
        MethodElement defaultConstructor = findDefaultConstructor(element).orElse(null);
        if (defaultConstructor == null) {
            return Optional.empty();
        }
        List<PropertyElement> beanProperties = introspectedProperties(element);
        if (beanProperties.isEmpty()) {
            return Optional.empty();
        }
        List<NamedProperty> serializationProperties = new ArrayList<>(beanProperties.size());
        List<BeanSerdeShape.BeanProperty> deserializationProperties = new ArrayList<>(beanProperties.size());
        List<String> ignoredDeserializationNames = new ArrayList<>(2);
        for (PropertyElement property : beanProperties) {
            PropertyAccess propertyAccess = resolvePropertyAccess(element, property);
            String name = stringValue(property, SerdeConfig.PROPERTY).orElse(property.getName());
            if (propertyAccess.readable()) {
                if (isSerialized(property)) {
                    BeanSerdeShape.BeanProperty beanProperty = resolveProperty(element, name, property, propertyAccess).orElse(null);
                    if (beanProperty == null) {
                        return Optional.empty();
                    }
                    serializationProperties.add(new NamedProperty(property.getName(), beanProperty));
                }
            }
            if (propertyAccess.writable()) {
                if (isDeserialized(property)) {
                    BeanSerdeShape.BeanProperty beanProperty = resolveProperty(element, name, property, propertyAccess).orElse(null);
                    if (beanProperty == null) {
                        return Optional.empty();
                    }
                    deserializationProperties.add(beanProperty);
                } else {
                    ignoredDeserializationNames.add(name);
                }
            }
        }
        List<BeanSerdeShape.BeanProperty> orderedSerializationProperties = SerdeSourceGenPropertyOrder.order(
            element,
            serializationProperties,
            named -> named.property().name(),
            NamedProperty::originalName,
            named -> isXmlAttribute(named.property())
        ).stream().map(NamedProperty::property).toList();
        deserializationProperties.sort((left, right) -> Boolean.compare(isXmlAttribute(right), isXmlAttribute(left)));
        return Optional.of(new BeanSerdeShape(
            defaultConstructor,
            orderedSerializationProperties,
            List.copyOf(deserializationProperties),
            List.copyOf(ignoredDeserializationNames),
            resolveIgnoreUnknown(element)
        ));
    }

    /**
     * The bean properties as the introspection resolves them. The list a class element caches can be
     * older than the metadata {@code SerdeAnnotationVisitor} rewrites, so the properties are resolved
     * again with the query the introspection visitor uses.
     *
     * @param element The bean type
     * @return The introspected properties
     */
    public static List<PropertyElement> introspectedProperties(ClassElement element) {
        return element.getBeanProperties(PropertyElementQuery.of(element));
    }

    /**
     * Whether the runtime object serializer writes the property.
     *
     * @param property The property
     * @return {@code true} if the property is serialized
     */
    public static boolean isSerialized(PropertyElement property) {
        return !booleanValue(property, SerdeConfig.IGNORED).orElse(false)
            && !booleanValue(property, SerdeConfig.IGNORED_SERIALIZATION).orElse(false)
            && canSerialize(property);
    }

    /**
     * Whether the runtime object deserializer reads the property.
     *
     * @param property The property
     * @return {@code true} if the property is deserialized
     */
    public static boolean isDeserialized(PropertyElement property) {
        return !booleanValue(property, SerdeConfig.IGNORED).orElse(false)
            && !booleanValue(property, SerdeConfig.IGNORED_DESERIALIZATION).orElse(false)
            && canDeserialize(property);
    }

    private static boolean canSerialize(PropertyElement property) {
        return SerdePropertyAccess.canSerialize(property.getAnnotationMetadata())
            && property.getReadMethod().map(method -> SerdePropertyAccess.canSerialize(method.getAnnotationMetadata())).orElse(true)
            && property.getWriteMethod().map(method -> SerdePropertyAccess.canSerialize(method.getAnnotationMetadata())).orElse(true)
            && property.getField().map(field -> SerdePropertyAccess.canSerialize(field.getAnnotationMetadata())).orElse(true);
    }

    private static boolean canDeserialize(PropertyElement property) {
        return SerdePropertyAccess.canDeserialize(property.getAnnotationMetadata())
            && property.getReadMethod().map(method -> SerdePropertyAccess.canDeserialize(method.getAnnotationMetadata())).orElse(true)
            && property.getWriteMethod().map(method -> SerdePropertyAccess.canDeserialize(method.getAnnotationMetadata())).orElse(true)
            && property.getField().map(field -> SerdePropertyAccess.canDeserialize(field.getAnnotationMetadata())).orElse(true);
    }

    /**
     * The unknown property policy declared on the type, mirroring the runtime object deserializer:
     * included properties always ignore the rest, otherwise the declared value wins over the configuration.
     */
    private static @Nullable Boolean resolveIgnoreUnknown(ClassElement element) {
        if (element.isAnnotationPresent(SerdeConfig.SerIncluded.class)) {
            return Boolean.TRUE;
        }
        return element.booleanValue(SerdeConfig.SerIgnored.class, SerdeConfig.SerIgnored.IGNORE_UNKNOWN).orElse(null);
    }

    private static boolean isBeanShapeCandidate(ClassElement element) {
        return !element.isInterface()
            && !element.isAbstract()
            && !element.isEnum()
            && !element.isRecord()
            && element.getTypeArguments().isEmpty();
    }

    private static Optional<ConstructorElement> findDefaultConstructor(ClassElement element) {
        return element.getAccessibleConstructors().stream()
            .filter(c -> c.getParameters().length == 0)
            .findFirst();
    }

    private static boolean isXmlAttribute(BeanSerdeShape.BeanProperty property) {
        return Boolean.parseBoolean(property.keyMetadata().get(SerdeConfig.XML_ATTRIBUTE_PROPERTY));
    }

    private static Optional<BeanSerdeShape.BeanProperty> resolveProperty(ClassElement element,
                                                                         String name,
                                                                         PropertyElement property,
                                                                         PropertyAccess propertyAccess) {
        ClassElement readType = property.getReadType().orElse(null);
        ClassElement writeType = property.getWriteType().orElse(null);
        ClassElement serializationType = readType != null ? readType : writeType;
        ClassElement deserializationType = writeType != null ? writeType : readType;
        if (serializationType == null || deserializationType == null) {
            return Optional.empty();
        }
        if (serializationType.isTypeVariable() || deserializationType.isTypeVariable()) {
            return Optional.empty();
        }
        Map<String, String> keyMetadata = resolveKeyMetadata(property);
        return Optional.of(new BeanSerdeShape.BeanProperty(
            name,
            serializationType,
            deserializationType,
            property.isNonNull(),
            property.isNullable(),
            keyMetadata,
            SerdeInclusionSourceGen.resolvePropertyInclude(element, property, keyMetadata),
            booleanValue(property, SerdeConfig.REQUIRED).orElse(false),
            aliases(property),
            propertyAccess.readMethod(),
            propertyAccess.writeMethod(),
            propertyAccess.readField(),
            propertyAccess.writeField()
        ));
    }

    private static Map<String, String> resolveKeyMetadata(PropertyElement property) {
        Map<String, String> metadata = CollectionUtils.newHashMap(10);
        booleanValue(property, SerdeConfig.XML_ATTRIBUTE_PROPERTY)
            .filter(Boolean::booleanValue)
            .ifPresent(value -> metadata.put(SerdeConfig.XML_ATTRIBUTE_PROPERTY, "true"));
        stringValue(property, SerdeConfig.XML_NAMESPACE)
            .ifPresent(value -> metadata.put(SerdeConfig.XML_NAMESPACE, value));
        booleanValue(property, SerdeConfig.XML_TEXT_PROPERTY)
            .filter(Boolean::booleanValue)
            .ifPresent(value -> metadata.put(SerdeConfig.XML_TEXT_PROPERTY, "true"));
        booleanValue(property, SerdeConfig.XML_CDATA_PROPERTY)
            .filter(Boolean::booleanValue)
            .ifPresent(value -> metadata.put(SerdeConfig.XML_CDATA_PROPERTY, "true"));
        booleanValue(property, SerdeConfig.XML_LIST_PROPERTY)
            .filter(Boolean::booleanValue)
            .ifPresent(value -> metadata.put(SerdeConfig.XML_LIST_PROPERTY, "true"));
        booleanValue(property, SerdeConfig.XML_MIXED_PROPERTY)
            .filter(Boolean::booleanValue)
            .ifPresent(value -> metadata.put(SerdeConfig.XML_MIXED_PROPERTY, "true"));
        booleanValue(property, SerdeConfig.META_ANNOTATION_PROPERTY)
            .ifPresent(value -> metadata.put(SerdeConfig.META_ANNOTATION_PROPERTY, value.toString()));
        stringValue(property, SerdeConfig.WRAPPER_PROPERTY)
            .ifPresent(value -> metadata.put(SerdeConfig.WRAPPER_PROPERTY, value));
        stringValue(property, SerdeConfig.XML_WRAPPER_NAMESPACE)
            .ifPresent(value -> metadata.put(SerdeConfig.XML_WRAPPER_NAMESPACE, value));
        stringValue(property, SerdeConfig.XML_DEFAULT_VALUE)
            .ifPresent(value -> metadata.put(SerdeConfig.XML_DEFAULT_VALUE, value));
        booleanValue(property, SerdeConfig.XML_NILLABLE)
            .ifPresent(value -> metadata.put(SerdeConfig.XML_NILLABLE, value.toString()));
        booleanValue(property, SerdeConfig.XML_WRAPPER_NILLABLE)
            .ifPresent(value -> metadata.put(SerdeConfig.XML_WRAPPER_NILLABLE, value.toString()));
        return Map.copyOf(metadata);
    }

    private static List<String> aliases(PropertyElement property) {
        String[] aliases = property.stringValues(SerdeConfig.class, SerdeConfig.ALIASES);
        if (aliases.length == 0) {
            aliases = property.getReadMethod().map(method -> method.stringValues(SerdeConfig.class, SerdeConfig.ALIASES)).orElse(aliases);
        }
        if (aliases.length == 0) {
            aliases = property.getWriteMethod().map(method -> method.stringValues(SerdeConfig.class, SerdeConfig.ALIASES)).orElse(aliases);
        }
        if (aliases.length == 0) {
            aliases = property.getField().map(field -> field.stringValues(SerdeConfig.class, SerdeConfig.ALIASES)).orElse(aliases);
        }
        return List.of(aliases);
    }

    private static Optional<String> stringValue(PropertyElement property, String member) {
        Optional<String> value = property.stringValue(SerdeConfig.class, member);
        if (value.isEmpty()) {
            value = property.getReadMethod().flatMap(method -> method.stringValue(SerdeConfig.class, member));
        }
        if (value.isEmpty()) {
            value = property.getWriteMethod().flatMap(method -> method.stringValue(SerdeConfig.class, member));
        }
        if (value.isEmpty()) {
            value = property.getField().flatMap(field -> field.stringValue(SerdeConfig.class, member));
        }
        return value;
    }

    private static Optional<Boolean> booleanValue(PropertyElement property, String member) {
        Optional<Boolean> value = booleanValue(property.getAnnotationMetadata(), member);
        if (value.isEmpty()) {
            value = property.getReadMethod().flatMap(method -> booleanValue(method.getAnnotationMetadata(), member));
        }
        if (value.isEmpty()) {
            value = property.getWriteMethod().flatMap(method -> booleanValue(method.getAnnotationMetadata(), member));
        }
        if (value.isEmpty()) {
            value = property.getField().flatMap(field -> booleanValue(field.getAnnotationMetadata(), member));
        }
        return value;
    }

    private static Optional<Boolean> booleanValue(AnnotationMetadata annotationMetadata, String member) {
        return annotationMetadata.booleanValue(SerdeConfig.class, member);
    }

    /**
     * Resolves the accessible read and write members of a property. A missing member in one direction
     * only excludes the property from that direction.
     */
    private static PropertyAccess resolvePropertyAccess(ClassElement element, PropertyElement property) {
        MethodElement readMethod = null;
        MethodElement writeMethod = null;
        FieldElement readField = null;
        FieldElement writeField = null;
        if (property.getReadAccessKind() == PropertyElement.AccessKind.FIELD) {
            readField = property.getField()
                .filter(field -> field.isAccessible(element, false))
                .orElse(null);
        } else {
            readMethod = property.getReadMethod().orElse(null);
        }
        if (property.getWriteAccessKind() == PropertyElement.AccessKind.FIELD) {
            writeField = property.getField()
                .filter(field -> !field.isFinal())
                .filter(field -> field.isAccessible(element, false))
                .orElse(null);
        } else {
            writeMethod = property.getWriteMethod().orElse(null);
        }
        return new PropertyAccess(readMethod, writeMethod, readField, writeField);
    }

    private record NamedProperty(String originalName, BeanSerdeShape.BeanProperty property) {
    }

    private record PropertyAccess(
        @Nullable MethodElement readMethod,
        @Nullable MethodElement writeMethod,
        @Nullable FieldElement readField,
        @Nullable FieldElement writeField) {

        boolean readable() {
            return readMethod != null || readField != null;
        }

        boolean writable() {
            return writeMethod != null || writeField != null;
        }
    }
}
