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
package io.micronaut.serde.processor.sourcegen.records;

import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.processor.sourcegen.SerdeInclusionSourceGen;
import io.micronaut.serde.processor.sourcegen.beans.BeanSerdeShapeResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves record shapes eligible for source-generated serdes.
 */
public final class RecordSerdeShapeResolver {

    public Optional<RecordSerdeShape> resolve(ClassElement element) {
        if (!element.isRecord() && !isConstructorBean(element)) {
            return Optional.empty();
        }
        if (!element.getTypeArguments().isEmpty()) {
            return Optional.empty();
        }
        MethodElement canonicalConstructor = element.getPrimaryConstructor().orElse(null);
        if (canonicalConstructor == null) {
            return Optional.empty();
        }
        Map<String, PropertyElement> propertiesByName = BeanSerdeShapeResolver.introspectedProperties(element).stream()
            .collect(Collectors.toMap(PropertyElement::getName, Function.identity()));
        List<RecordSerdeShape.RecordComponent> components = new ArrayList<>();
        for (ParameterElement parameter : canonicalConstructor.getParameters()) {
            if (parameter.getType().isTypeVariable()) {
                return Optional.empty();
            }
            PropertyElement propertyElement = propertiesByName.get(parameter.getName());
            if (propertyElement == null) {
                return Optional.empty();
            }
            Map<String, String> keyMetadata = resolveKeyMetadata(propertyElement);
            components.add(new RecordSerdeShape.RecordComponent(
                parameter.getName(),
                stringValue(propertyElement, SerdeConfig.PROPERTY).orElse(parameter.getName()),
                parameter.getType(),
                keyMetadata,
                SerdeInclusionSourceGen.resolvePropertyInclude(element, propertyElement, keyMetadata),
                booleanValue(propertyElement, SerdeConfig.REQUIRED).orElse(parameter.booleanValue(SerdeConfig.class, SerdeConfig.REQUIRED).orElse(false)),
                aliases(propertyElement, parameter),
                // The runtime merges the parameter and the property metadata for a constructor argument,
                // and an explicit nullable declaration wins over a non-null one
                !(parameter.isNullable() || propertyElement.isNullable()) && (parameter.isNonNull() || propertyElement.isNonNull()),
                parameter.isNullable() || propertyElement.isNullable(),
                propertyElement
            ));
        }
        return Optional.of(new RecordSerdeShape(canonicalConstructor, List.copyOf(components)));
    }

    /**
     * Whether a class binds every property through its primary constructor, so that the record
     * generators apply: the constructor takes at least one parameter, every parameter names a bean
     * property of the same type, and every bean property is such a parameter. A property outside the
     * constructor would be written or set by the runtime serdes through other means.
     *
     * @param element The type
     * @return {@code true} if the type is bound through its constructor like a record
     */
    public static boolean isConstructorBean(ClassElement element) {
        if (element.isRecord() || element.isInterface() || element.isAbstract() || element.isEnum()) {
            return false;
        }
        MethodElement primaryConstructor = element.getPrimaryConstructor().orElse(null);
        if (!(primaryConstructor instanceof ConstructorElement) || primaryConstructor.getParameters().length == 0) {
            return false;
        }
        List<PropertyElement> beanProperties = BeanSerdeShapeResolver.introspectedProperties(element);
        Map<String, PropertyElement> propertiesByName = CollectionUtils.newHashMap(beanProperties.size());
        for (PropertyElement property : beanProperties) {
            propertiesByName.put(property.getName(), property);
        }
        ParameterElement[] parameters = primaryConstructor.getParameters();
        if (parameters.length != beanProperties.size()) {
            return false;
        }
        for (ParameterElement parameter : parameters) {
            PropertyElement property = propertiesByName.get(parameter.getName());
            if (property == null
                || property.getReadMethod().isEmpty()
                || !property.getType().getName().equals(parameter.getType().getName())) {
                return false;
            }
        }
        return true;
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

    private static List<String> aliases(PropertyElement property, ParameterElement parameter) {
        String[] aliases = property.stringValues(SerdeConfig.class, SerdeConfig.ALIASES);
        if (aliases.length == 0) {
            aliases = parameter.stringValues(SerdeConfig.class, SerdeConfig.ALIASES);
        }
        if (aliases.length == 0) {
            aliases = property.getReadMethod().map(method -> method.stringValues(SerdeConfig.class, SerdeConfig.ALIASES)).orElse(aliases);
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
}
