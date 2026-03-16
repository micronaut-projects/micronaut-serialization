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
package io.micronaut.serde.processor.sourcegen;

import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;

import java.lang.annotation.Annotation;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Analyzes candidate types and decides source-generation eligibility and fallback reasons.
 */
public final class SimpleSerdeShapeAnalyzer {
    private static final String JACKSON_JSON_INCLUDE = "com.fasterxml.jackson.annotation.JsonInclude";
    private static final String JACKSON_JSON_VALUE = "com.fasterxml.jackson.annotation.JsonValue";
    private static final String JACKSON2_JSON_VALUE = "tools.jackson.annotation.JsonValue";
    private static final String JACKSON_JSON_PROPERTY_ORDER = "com.fasterxml.jackson.annotation.JsonPropertyOrder";
    private static final String JACKSON2_JSON_PROPERTY_ORDER = "tools.jackson.annotation.JsonPropertyOrder";
    private static final String SERDEABLE_SERIALIZABLE = Serdeable.Serializable.class.getName();
    private static final String SERDEABLE_DESERIALIZABLE = Serdeable.Deserializable.class.getName();
    private static final String JACKSON_ANNOTATION_PREFIX = "com.fasterxml.jackson.annotation.";
    private static final String BSON_REPRESENTATION = "org.bson.codecs.pojo.annotations.BsonRepresentation";

    @SuppressWarnings("java:S3776")
    public SimpleSerdeShapeDecision analyze(ClassElement element) {
        EnumSet<SimpleSerdeShapeDecision.FallbackReason> serializerReasons = EnumSet.noneOf(SimpleSerdeShapeDecision.FallbackReason.class);
        EnumSet<SimpleSerdeShapeDecision.FallbackReason> deserializerReasons = EnumSet.noneOf(SimpleSerdeShapeDecision.FallbackReason.class);

        if (element.hasDeclaredAnnotation(SerdeConfig.SerSubtyped.class)) {
            serializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.SUBTYPED);
            deserializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.SUBTYPED);
        }
        if (hasSubtypedPropertyTypes(element)) {
            serializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.SUBTYPED);
            deserializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.SUBTYPED);
        }
        if (hasAnnotation(element, SerdeConfig.SerUnwrapped.class)) {
            serializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNWRAPPED);
            deserializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNWRAPPED);
        }
        if (hasAnnotation(element, SerdeConfig.SerAnyGetter.class)) {
            serializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.ANY_GETTER);
        }
        if (hasAnnotation(element, SerdeConfig.SerAnySetter.class)) {
            deserializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.ANY_SETTER);
        }
        if (hasJsonInclude(element)) {
            serializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.INCLUDE);
            deserializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.INCLUDE);
        }
        if (hasUnsupportedSerdeConfigMetadata(element.getAnnotationMetadata())) {
            serializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
            deserializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
        }
        if (element.hasAnnotation(SerdeConfig.SerIgnored.class)
            || hasAnnotation(element, SerdeConfig.SerIgnored.class)
            || element.hasDeclaredAnnotation(SerdeConfig.SerIgnored.class)) {
            serializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
            deserializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
        }
        if (hasPropertyNamedIgnored(element)) {
            serializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
            deserializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
        }
        if (!hasJsonInclude(element)
            && (element.hasAnnotation(SerdeConfig.SerIncluded.class)
            || hasAnnotation(element, SerdeConfig.SerIncluded.class)
            || element.hasDeclaredAnnotation(SerdeConfig.SerIncluded.class))) {
            serializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
            deserializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
        }
        if (hasPropertyOrderConfig(element)) {
            serializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
            deserializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
        }
        if (serializerReasons.isEmpty() && deserializerReasons.isEmpty() && hasPotentialGlobalOrderingConflict(element)) {
            serializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
            deserializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
        }
        if (!element.isEnum() && hasAnnotation(element, SerdeConfig.SerValue.class)) {
            serializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
            deserializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
        }
        if (!element.isEnum() && (hasAnnotation(element, JACKSON_JSON_VALUE) || hasAnnotation(element, JACKSON2_JSON_VALUE))) {
            serializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
            deserializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
        }
        if (!element.isEnum() && hasJsonValueInPropertyTypes(element)) {
            serializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
            deserializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
        }
        if (hasCustomSerdeClassOverride(element)) {
            serializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
            deserializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
        }
        if (hasCustomNaming(element)) {
            serializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
            deserializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
        }
        if (serializerReasons.isEmpty() && deserializerReasons.isEmpty() && hasPotentialGlobalNamingConflict(element)) {
            serializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
            deserializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
        }
        if (hasSerializeAsOverride(element)) {
            serializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
        }
        if (hasDeserializeAsOverride(element)) {
            deserializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
        }
        if (hasPropertyLevelSerializableOverride(element)) {
            serializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
        }
        if (hasPropertyLevelDeserializableOverride(element)) {
            deserializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
        }
        SerdeConfig.SerCreatorMode creatorMode = element.getPrimaryConstructor()
            .flatMap(c -> c.enumValue(Creator.class, "mode", SerdeConfig.SerCreatorMode.class))
            .orElse(SerdeConfig.SerCreatorMode.PROPERTIES);
        if (creatorMode == SerdeConfig.SerCreatorMode.DELEGATING) {
            serializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.COMPLEX_CREATOR);
            deserializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.COMPLEX_CREATOR);
        }

        SimpleSerdeShapeDecision.ShapeKind shapeKind = resolveShapeKind(element);
        if (shapeKind == SimpleSerdeShapeDecision.ShapeKind.UNSUPPORTED) {
            serializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
            deserializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
        }
        if (shapeKind != SimpleSerdeShapeDecision.ShapeKind.UNSUPPORTED && hasDirectIterableProperties(element)) {
            serializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
            deserializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
        }
        if (shapeKind != SimpleSerdeShapeDecision.ShapeKind.UNSUPPORTED && hasCustomPropertyNames(element)) {
            serializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
            deserializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
        }
        if (shapeKind != SimpleSerdeShapeDecision.ShapeKind.UNSUPPORTED && hasUnsupportedPropertySerdeConfig(element)) {
            serializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
            deserializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
        }
        if (shapeKind != SimpleSerdeShapeDecision.ShapeKind.UNSUPPORTED && hasAnnotation(element, BSON_REPRESENTATION)) {
            serializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
            deserializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
        }
        if (shapeKind == SimpleSerdeShapeDecision.ShapeKind.ENUM && hasComplexEnumCustomization(element)) {
            serializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.COMPLEX_ENUM);
            deserializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.COMPLEX_ENUM);
        }
        if (shapeKind == SimpleSerdeShapeDecision.ShapeKind.ENUM && hasEnumPropertyOverrides(element)) {
            serializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.COMPLEX_ENUM);
            deserializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.COMPLEX_ENUM);
        }
        if (hasUnsupportedJacksonAnnotation(element) && serializerReasons.isEmpty() && deserializerReasons.isEmpty()) {
            serializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
            deserializerReasons.add(SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
        }

        return new SimpleSerdeShapeDecision(
            shapeKind,
            serializerReasons.isEmpty(),
            deserializerReasons.isEmpty(),
            serializerReasons,
            deserializerReasons
        );
    }

    private boolean hasAnnotation(ClassElement element, Class<? extends Annotation> annotation) {
        if (element.getPrimaryConstructor().map(c -> hasAnnotation(c, annotation)).orElse(false)) {
            return true;
        }
        if (element.getBeanProperties().stream().anyMatch(p -> p.hasAnnotation(annotation))) {
            return true;
        }
        if (!element.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyInstance().onlyDeclared().annotated(a -> a.hasAnnotation(annotation))).isEmpty()) {
            return true;
        }
        return !element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyInstance().onlyDeclared().annotated(a -> a.hasAnnotation(annotation))).isEmpty();
    }

    private boolean hasAnnotation(ClassElement element, String annotationName) {
        if (element.getPrimaryConstructor().map(c -> hasAnnotation(c, annotationName)).orElse(false)) {
            return true;
        }
        if (element.getBeanProperties().stream().anyMatch(p -> p.hasAnnotation(annotationName))) {
            return true;
        }
        if (!element.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyInstance().onlyDeclared().annotated(a -> a.hasAnnotation(annotationName))).isEmpty()) {
            return true;
        }
        return !element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyInstance().onlyDeclared().annotated(a -> a.hasAnnotation(annotationName))).isEmpty();
    }

    private boolean hasJsonInclude(ClassElement element) {
        if (element.hasDeclaredAnnotation(JACKSON_JSON_INCLUDE) || hasAnnotation(element, JACKSON_JSON_INCLUDE)) {
            return true;
        }
        return element.getEnclosingType().map(this::hasJsonInclude).orElse(false);
    }

    private boolean hasUnsupportedJacksonAnnotation(ClassElement element) {
        if (hasJacksonAnnotationInTypeHierarchy(element, new HashSet<>())) {
            return true;
        }
        for (PropertyElement property : element.getBeanProperties()) {
            ClassElement serializationType = property.getReadMethod().map(MethodElement::getReturnType).orElse(property.getType());
            if (hasJacksonAnnotationInTypeHierarchy(serializationType, new HashSet<>())) {
                return true;
            }
            ClassElement deserializationType = property.getWriteMethod().map(m -> m.getParameters()[0].getType()).orElse(property.getType());
            if (hasJacksonAnnotationInTypeHierarchy(deserializationType, new HashSet<>())) {
                return true;
            }
            if (hasJacksonAnnotationNames(property.getAnnotationNames())) {
                return true;
            }
            if (property.getReadMethod().map(MethodElement::getAnnotationNames).map(this::hasJacksonAnnotationNames).orElse(false)) {
                return true;
            }
            if (property.getWriteMethod().map(MethodElement::getAnnotationNames).map(this::hasJacksonAnnotationNames).orElse(false)) {
                return true;
            }
        }
        if (!element.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyInstance().onlyDeclared()
            .annotated(a -> hasJacksonAnnotationNames(a.getAnnotationNames()))).isEmpty()) {
            return true;
        }
        if (!element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyInstance().onlyDeclared()
            .annotated(a -> hasJacksonAnnotationNames(a.getAnnotationNames()))).isEmpty()) {
            return true;
        }
        return element.getEnclosingType().map(this::hasUnsupportedJacksonAnnotation).orElse(false);
    }

    private boolean hasJacksonAnnotationNames(Set<String> annotationNames) {
        return annotationNames.stream().anyMatch(name -> name.startsWith(JACKSON_ANNOTATION_PREFIX));
    }

    private boolean hasJacksonAnnotationInTypeHierarchy(ClassElement classElement, Set<String> visited) {
        if (!visited.add(classElement.getName())) {
            return false;
        }
        if (hasJacksonAnnotationNames(classElement.getAnnotationNames())) {
            return true;
        }
        for (ClassElement interfaceElement : classElement.getInterfaces()) {
            if (hasJacksonAnnotationInTypeHierarchy(interfaceElement, visited)) {
                return true;
            }
        }
        return classElement.getSuperType().map(superType -> hasJacksonAnnotationInTypeHierarchy(superType, visited)).orElse(false);
    }

    private SimpleSerdeShapeDecision.ShapeKind resolveShapeKind(ClassElement element) {
        if (element.isEnum()) {
            return SimpleSerdeShapeDecision.ShapeKind.ENUM;
        }
        if (element.isRecord()) {
            return SimpleSerdeShapeDecision.ShapeKind.RECORD;
        }
        if (isDefaultConstructorBean(element)) {
            return SimpleSerdeShapeDecision.ShapeKind.DEFAULT_CONSTRUCTOR_BEAN;
        }
        return SimpleSerdeShapeDecision.ShapeKind.UNSUPPORTED;
    }

    private boolean isDefaultConstructorBean(ClassElement element) {
        if (element.isInterface() || element.isEnum() || element.isRecord()) {
            return false;
        }
        boolean hasDefaultConstructor = element.getAccessibleConstructors().stream().anyMatch(c -> c.getParameters().length == 0);
        if (!hasDefaultConstructor) {
            return false;
        }
        List<PropertyElement> beanProperties = element.getBeanProperties();
        if (beanProperties.isEmpty()) {
            return false;
        }
        for (PropertyElement property : beanProperties) {
            if (property.getReadMethod().isEmpty() || property.getWriteMethod().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean hasDirectIterableProperties(ClassElement element) {
        for (PropertyElement property : element.getBeanProperties()) {
            ClassElement serializationType = property.getReadMethod().map(m -> m.getReturnType()).orElse(property.getType());
            if (isDirectIterableType(serializationType)) {
                return true;
            }
            ClassElement deserializationType = property.getWriteMethod().map(m -> m.getParameters()[0].getType()).orElse(property.getType());
            if (isDirectIterableType(deserializationType)) {
                return true;
            }
        }
        return false;
    }

    private boolean isDirectIterableType(ClassElement type) {
        return "java.lang.Iterable".equals(type.getName());
    }

    private boolean hasComplexEnumCustomization(ClassElement element) {
        if (hasAnnotation(element, SerdeConfig.SerValue.class)) {
            return true;
        }
        if (element.getPrimaryConstructor().map(c -> c.hasDeclaredAnnotation(Creator.class)).orElse(false)) {
            return true;
        }
        return !element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared().annotated(a -> a.hasDeclaredAnnotation(Creator.class))).isEmpty();
    }

    private boolean hasEnumPropertyOverrides(ClassElement element) {
        if (!element.isEnum()) {
            return false;
        }
        return !element.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyDeclared().annotated(annotationMetadata -> {
            String configured = annotationMetadata.stringValue(SerdeConfig.class, SerdeConfig.PROPERTY).orElse(null);
            return configured != null && !configured.isBlank();
        })).isEmpty();
    }

    private boolean hasSubtypedPropertyTypes(ClassElement element) {
        for (PropertyElement property : element.getBeanProperties()) {
            ClassElement serializationType = property.getReadMethod().map(MethodElement::getReturnType).orElse(property.getType());
            if (serializationType.hasDeclaredAnnotation(SerdeConfig.SerSubtyped.class)) {
                return true;
            }
            ClassElement deserializationType = property.getWriteMethod().map(m -> m.getParameters()[0].getType()).orElse(property.getType());
            if (deserializationType.hasDeclaredAnnotation(SerdeConfig.SerSubtyped.class)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasCustomPropertyNames(ClassElement element) {
        for (PropertyElement property : element.getBeanProperties()) {
            String configured = property.stringValue(SerdeConfig.class, SerdeConfig.PROPERTY).orElse(null);
            if (configured != null && !configured.equals(property.getName())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasUnsupportedPropertySerdeConfig(ClassElement element) {
        for (PropertyElement property : element.getBeanProperties()) {
            if (hasUnsupportedSerdeConfig(property)) {
                return true;
            }
            if (property.getReadMethod().map(this::hasUnsupportedSerdeConfig).orElse(false)) {
                return true;
            }
            if (property.getWriteMethod().map(this::hasUnsupportedSerdeConfig).orElse(false)) {
                return true;
            }
        }
        var primaryConstructor = element.getPrimaryConstructor();
        if (element.isRecord() && primaryConstructor.isPresent()) {
            for (ParameterElement parameter : primaryConstructor.orElseThrow().getParameters()) {
                if (hasUnsupportedSerdeConfig(parameter)) {
                    return true;
                }
            }
        }
        if (!element.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyInstance().onlyDeclared().annotated(this::hasUnsupportedSerdeConfigMetadata)).isEmpty()) {
            return true;
        }
        return !element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyInstance().onlyDeclared().annotated(this::hasUnsupportedSerdeConfigMetadata)).isEmpty();
    }

    private boolean hasPropertyOrderConfig(ClassElement element) {
        if (hasAnnotation(element, JACKSON_JSON_PROPERTY_ORDER) || hasAnnotation(element, JACKSON2_JSON_PROPERTY_ORDER)) {
            return true;
        }
        if (element.isAnnotationPresent(SerdeConfig.META_ANNOTATION_PROPERTY_ORDER)) {
            return true;
        }
        for (PropertyElement property : element.getBeanProperties()) {
            if (property.isAnnotationPresent(SerdeConfig.META_ANNOTATION_PROPERTY_ORDER)
                || property.stringValues(SerdeConfig.META_ANNOTATION_PROPERTY_ORDER).length > 0
                || property.getReadMethod().map(m -> m.isAnnotationPresent(SerdeConfig.META_ANNOTATION_PROPERTY_ORDER)).orElse(false)
                || property.getReadMethod().map(m -> m.stringValues(SerdeConfig.META_ANNOTATION_PROPERTY_ORDER).length > 0).orElse(false)
                || property.getWriteMethod().map(m -> m.isAnnotationPresent(SerdeConfig.META_ANNOTATION_PROPERTY_ORDER)).orElse(false)) {
                return true;
            }
        }
        if (!element.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyInstance().onlyDeclared()
            .annotated(a -> a.isAnnotationPresent(SerdeConfig.META_ANNOTATION_PROPERTY_ORDER)
                || a.stringValues(SerdeConfig.META_ANNOTATION_PROPERTY_ORDER).length > 0)).isEmpty()) {
            return true;
        }
        if (!element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyInstance().onlyDeclared()
            .annotated(a -> a.isAnnotationPresent(SerdeConfig.META_ANNOTATION_PROPERTY_ORDER)
                || a.stringValues(SerdeConfig.META_ANNOTATION_PROPERTY_ORDER).length > 0)).isEmpty()) {
            return true;
        }
        return element.getPrimaryConstructor().map(c -> c.isAnnotationPresent(SerdeConfig.META_ANNOTATION_PROPERTY_ORDER)
            || c.stringValues(SerdeConfig.META_ANNOTATION_PROPERTY_ORDER).length > 0).orElse(false);
    }

    private boolean hasJsonValueInPropertyTypes(ClassElement element) {
        for (PropertyElement property : element.getBeanProperties()) {
            ClassElement serializationType = property.getReadMethod().map(MethodElement::getReturnType).orElse(property.getType());
            if (hasAnnotation(serializationType, JACKSON_JSON_VALUE) || hasAnnotation(serializationType, JACKSON2_JSON_VALUE)) {
                return true;
            }
            ClassElement deserializationType = property.getWriteMethod().map(m -> m.getParameters()[0].getType()).orElse(property.getType());
            if (hasAnnotation(deserializationType, JACKSON_JSON_VALUE) || hasAnnotation(deserializationType, JACKSON2_JSON_VALUE)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPropertyLevelSerializableOverride(ClassElement element) {
        if (element.getBeanProperties().stream().anyMatch(p -> p.hasAnnotation(SERDEABLE_SERIALIZABLE))) {
            return true;
        }
        if (!element.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyInstance().onlyDeclared()
            .annotated(a -> a.hasAnnotation(SERDEABLE_SERIALIZABLE))).isEmpty()) {
            return true;
        }
        if (!element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyInstance().onlyDeclared()
            .annotated(a -> a.hasAnnotation(SERDEABLE_SERIALIZABLE))).isEmpty()) {
            return true;
        }
        return element.getPrimaryConstructor().map(c -> {
            for (ParameterElement parameter : c.getParameters()) {
                if (parameter.hasAnnotation(SERDEABLE_SERIALIZABLE)) {
                    return true;
                }
            }
            return false;
        }).orElse(false);
    }

    private boolean hasPropertyLevelDeserializableOverride(ClassElement element) {
        if (element.getBeanProperties().stream().anyMatch(p -> p.hasAnnotation(SERDEABLE_DESERIALIZABLE))) {
            return true;
        }
        if (!element.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyInstance().onlyDeclared()
            .annotated(a -> a.hasAnnotation(SERDEABLE_DESERIALIZABLE))).isEmpty()) {
            return true;
        }
        if (!element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyInstance().onlyDeclared()
            .annotated(a -> a.hasAnnotation(SERDEABLE_DESERIALIZABLE))).isEmpty()) {
            return true;
        }
        return element.getPrimaryConstructor().map(c -> {
            for (ParameterElement parameter : c.getParameters()) {
                if (parameter.hasAnnotation(SERDEABLE_DESERIALIZABLE)) {
                    return true;
                }
            }
            return false;
        }).orElse(false);
    }

    private boolean hasCustomSerdeClassOverride(ClassElement element) {
        AnnotationMetadata annotationMetadata = element.getAnnotationMetadata();
        return annotationMetadata.classValue(SerdeConfig.class, SerdeConfig.SERIALIZER_CLASS).isPresent()
            || annotationMetadata.classValue(SerdeConfig.class, SerdeConfig.DESERIALIZER_CLASS).isPresent();
    }

    private boolean hasPotentialGlobalNamingConflict(ClassElement element) {
        for (PropertyElement property : element.getBeanProperties()) {
            if (containsUppercase(property.getName())) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("java:S3776")
    private boolean hasPotentialGlobalOrderingConflict(ClassElement element) {
        List<? extends Element> fields = element.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyInstance().onlyDeclared());
        if (fields.size() >= 3) {
            if (fields.stream().anyMatch(f -> f.getName().length() != 1)) {
                return false;
            }
            String previous = fields.get(0).getName();
            for (int i = 1; i < fields.size(); i++) {
                String current = fields.get(i).getName();
                if (previous.compareTo(current) > 0) {
                    return true;
                }
                previous = current;
            }
            return false;
        }

        List<PropertyElement> properties = element.getBeanProperties();
        if (properties.size() >= 3) {
            if (properties.stream().anyMatch(p -> p.getName().length() != 1)) {
                return false;
            }
            String previous = properties.get(0).getName();
            for (int i = 1; i < properties.size(); i++) {
                String current = properties.get(i).getName();
                if (previous.compareTo(current) > 0) {
                    return true;
                }
                previous = current;
            }
        }
        return false;
    }

    private boolean containsUppercase(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isUpperCase(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPropertyNamedIgnored(ClassElement element) {
        for (PropertyElement property : element.getBeanProperties()) {
            if ("ignored".equals(property.getName())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasUnsupportedSerdeConfig(Element element) {
        return element.booleanValue(SerdeConfig.class, SerdeConfig.IGNORED).orElse(false)
            || element.booleanValue(SerdeConfig.class, SerdeConfig.IGNORED_SERIALIZATION).orElse(false)
            || element.booleanValue(SerdeConfig.class, SerdeConfig.IGNORED_DESERIALIZATION).orElse(false)
            || element.stringValue(SerdeConfig.class, SerdeConfig.FILTER).isPresent()
            || element.booleanValue(SerdeConfig.class, SerdeConfig.REQUIRED).orElse(false)
            || element.booleanValue(SerdeConfig.class, SerdeConfig.READ_ONLY).orElse(false)
            || element.booleanValue(SerdeConfig.class, SerdeConfig.WRITE_ONLY).orElse(false)
            || hasSerializeAsOverride(element)
            || hasDeserializeAsOverride(element)
            || hasCustomNaming(element)
            || element.classValue(SerdeConfig.class, SerdeConfig.SERIALIZER_CLASS).isPresent()
            || element.classValue(SerdeConfig.class, SerdeConfig.DESERIALIZER_CLASS).isPresent();
    }

    private boolean hasUnsupportedSerdeConfigMetadata(AnnotationMetadata annotationMetadata) {
        return annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.IGNORED).orElse(false)
            || annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.IGNORED_SERIALIZATION).orElse(false)
            || annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.IGNORED_DESERIALIZATION).orElse(false)
            || annotationMetadata.stringValue(SerdeConfig.class, SerdeConfig.FILTER).isPresent()
            || annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.REQUIRED).orElse(false)
            || annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.READ_ONLY).orElse(false)
            || annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.WRITE_ONLY).orElse(false)
            || hasSerializeAsOverride(annotationMetadata)
            || hasDeserializeAsOverride(annotationMetadata)
            || hasCustomNaming(annotationMetadata)
            || annotationMetadata.classValue(SerdeConfig.class, SerdeConfig.SERIALIZER_CLASS).isPresent()
            || annotationMetadata.classValue(SerdeConfig.class, SerdeConfig.DESERIALIZER_CLASS).isPresent();
    }

    private boolean hasSerializeAsOverride(Element element) {
        return hasSerializeAsOverride(element.getAnnotationMetadata());
    }

    private boolean hasSerializeAsOverride(AnnotationMetadata annotationMetadata) {
        return annotationMetadata.classValue(SerdeConfig.class, SerdeConfig.SERIALIZE_AS).isPresent();
    }

    private boolean hasDeserializeAsOverride(Element element) {
        return hasDeserializeAsOverride(element.getAnnotationMetadata());
    }

    private boolean hasDeserializeAsOverride(AnnotationMetadata annotationMetadata) {
        return annotationMetadata.classValue(SerdeConfig.class, SerdeConfig.DESERIALIZE_AS).isPresent();
    }

    private boolean hasCustomNaming(Element element) {
        return hasCustomNaming(element.getAnnotationMetadata());
    }

    private boolean hasCustomNaming(ClassElement element) {
        return hasCustomNaming(element.getAnnotationMetadata());
    }

    private boolean hasCustomNaming(AnnotationMetadata annotationMetadata) {
        String naming = annotationMetadata.stringValue(SerdeConfig.class, SerdeConfig.NAMING).orElse(null);
        if (naming != null && !naming.equals(PropertyNamingStrategy.IDENTITY.getClass().getName())) {
            return true;
        }
        return annotationMetadata.stringValue(SerdeConfig.class, SerdeConfig.RUNTIME_NAMING).isPresent();
    }

    private boolean hasAnnotation(MethodElement methodElement, Class<? extends Annotation> annotation) {
        if (methodElement.hasAnnotation(annotation)) {
            return true;
        }
        for (ParameterElement parameter : methodElement.getParameters()) {
            if (parameter.hasAnnotation(annotation)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnnotation(MethodElement methodElement, String annotationName) {
        if (methodElement.hasAnnotation(annotationName)) {
            return true;
        }
        for (ParameterElement parameter : methodElement.getParameters()) {
            if (parameter.hasAnnotation(annotationName)) {
                return true;
            }
        }
        return false;
    }

}
