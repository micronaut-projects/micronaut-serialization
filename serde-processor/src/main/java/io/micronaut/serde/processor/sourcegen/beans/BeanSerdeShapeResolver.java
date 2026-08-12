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

import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.serde.config.annotation.SerdeConfig;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves bean shapes eligible for source-generated serdes.
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
        return resolveProperties(element)
            .map(properties -> new BeanSerdeShape(defaultConstructor, List.copyOf(properties)));
    }

    private static boolean isBeanShapeCandidate(ClassElement element) {
        return !element.isInterface()
            && !element.isEnum()
            && !element.isRecord()
            && element.getTypeArguments().isEmpty();
    }

    private static Optional<ConstructorElement> findDefaultConstructor(ClassElement element) {
        return element.getAccessibleConstructors().stream()
            .filter(c -> c.getParameters().length == 0)
            .findFirst();
    }

    private static Optional<List<BeanSerdeShape.BeanProperty>> resolveProperties(ClassElement element) {
        List<PropertyElement> beanProperties = element.getBeanProperties();
        if (beanProperties.isEmpty()) {
            return Optional.empty();
        }
        List<BeanSerdeShape.BeanProperty> properties = new ArrayList<>(beanProperties.size());
        for (PropertyElement property : beanProperties) {
            BeanSerdeShape.BeanProperty beanProperty = resolveProperty(element, property).orElse(null);
            if (beanProperty == null) {
                return Optional.empty();
            }
            properties.add(beanProperty);
        }
        properties.sort((left, right) -> Boolean.compare(isXmlAttribute(right), isXmlAttribute(left)));
        return Optional.of(properties);
    }

    private static boolean isXmlAttribute(BeanSerdeShape.BeanProperty property) {
        return Boolean.parseBoolean(property.keyMetadata().get(SerdeConfig.XML_ATTRIBUTE_PROPERTY));
    }

    private static Optional<BeanSerdeShape.BeanProperty> resolveProperty(ClassElement element, PropertyElement property) {
        PropertyAccess propertyAccess = resolvePropertyAccess(element, property).orElse(null);
        if (propertyAccess == null) {
            return Optional.empty();
        }
        ClassElement serializationType = property.getReadType().orElse(null);
        ClassElement deserializationType = property.getWriteType().orElse(null);
        if (serializationType == null || deserializationType == null) {
            return Optional.empty();
        }
        if (serializationType.isTypeVariable() || deserializationType.isTypeVariable()) {
            return Optional.empty();
        }
        return Optional.of(new BeanSerdeShape.BeanProperty(
            stringValue(property, SerdeConfig.PROPERTY).orElse(property.getName()),
            serializationType,
            deserializationType,
            property.isNonNull(),
            property.isNullable(),
            resolveKeyMetadata(property),
            propertyAccess.readMethod(),
            propertyAccess.writeMethod(),
            propertyAccess.readField(),
            propertyAccess.writeField()
        ));
    }

    private static Map<String, String> resolveKeyMetadata(PropertyElement property) {
        Map<String, String> metadata = new HashMap<>(7);
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
        booleanValue(property, SerdeConfig.META_ANNOTATION_PROPERTY)
            .ifPresent(value -> metadata.put(SerdeConfig.META_ANNOTATION_PROPERTY, value.toString()));
        stringValue(property, SerdeConfig.WRAPPER_PROPERTY)
            .ifPresent(value -> metadata.put(SerdeConfig.WRAPPER_PROPERTY, value));
        stringValue(property, SerdeConfig.XML_WRAPPER_NAMESPACE)
            .ifPresent(value -> metadata.put(SerdeConfig.XML_WRAPPER_NAMESPACE, value));
        return Map.copyOf(metadata);
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
        Optional<Boolean> value = property.booleanValue(SerdeConfig.class, member);
        if (value.isEmpty()) {
            value = property.getReadMethod().flatMap(method -> method.booleanValue(SerdeConfig.class, member));
        }
        if (value.isEmpty()) {
            value = property.getWriteMethod().flatMap(method -> method.booleanValue(SerdeConfig.class, member));
        }
        if (value.isEmpty()) {
            value = property.getField().flatMap(field -> field.booleanValue(SerdeConfig.class, member));
        }
        return value;
    }

    private static Optional<PropertyAccess> resolvePropertyAccess(ClassElement element, PropertyElement property) {
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
        if ((readMethod == null && readField == null) || (writeMethod == null && writeField == null)) {
            return Optional.empty();
        }
        return Optional.of(new PropertyAccess(readMethod, writeMethod, readField, writeField));
    }

    private record PropertyAccess(
        @Nullable MethodElement readMethod,
        @Nullable MethodElement writeMethod,
        @Nullable FieldElement readField,
        @Nullable FieldElement writeField) {
    }
}
