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
import io.micronaut.serde.config.annotation.SerdeConfig;

import java.lang.annotation.Annotation;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SimpleSerdeShapeAnalyzer {
    private static final String JACKSON_JSON_INCLUDE = "com.fasterxml.jackson.annotation.JsonInclude";
    private static final String JACKSON_ANNOTATION_PREFIX = "com.fasterxml.jackson.annotation.";
    private static final String BSON_REPRESENTATION = "org.bson.codecs.pojo.annotations.BsonRepresentation";

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
        if (element.getBeanProperties().stream().anyMatch(p -> p.hasAnnotation(annotation))) {
            return true;
        }
        if (!element.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyInstance().onlyDeclared().annotated(a -> a.hasDeclaredAnnotation(annotation))).isEmpty()) {
            return true;
        }
        return !element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyInstance().onlyDeclared().annotated(a -> a.hasDeclaredAnnotation(annotation))).isEmpty();
    }

    private boolean hasAnnotation(ClassElement element, String annotationName) {
        if (element.getBeanProperties().stream().anyMatch(p -> p.hasAnnotation(annotationName))) {
            return true;
        }
        if (!element.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyInstance().onlyDeclared().annotated(a -> a.hasDeclaredAnnotation(annotationName))).isEmpty()) {
            return true;
        }
        return !element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyInstance().onlyDeclared().annotated(a -> a.hasDeclaredAnnotation(annotationName))).isEmpty();
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
        if (element.isRecord() && element.getPrimaryConstructor().isPresent()) {
            for (ParameterElement parameter : element.getPrimaryConstructor().get().getParameters()) {
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

    private boolean hasUnsupportedSerdeConfig(Element element) {
        return element.booleanValue(SerdeConfig.class, SerdeConfig.IGNORED).orElse(false)
            || element.booleanValue(SerdeConfig.class, SerdeConfig.IGNORED_SERIALIZATION).orElse(false)
            || element.booleanValue(SerdeConfig.class, SerdeConfig.IGNORED_DESERIALIZATION).orElse(false)
            || element.classValue(SerdeConfig.class, SerdeConfig.SERIALIZER_CLASS).isPresent()
            || element.classValue(SerdeConfig.class, SerdeConfig.DESERIALIZER_CLASS).isPresent();
    }

    private boolean hasUnsupportedSerdeConfigMetadata(AnnotationMetadata annotationMetadata) {
        return annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.IGNORED).orElse(false)
            || annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.IGNORED_SERIALIZATION).orElse(false)
            || annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.IGNORED_DESERIALIZATION).orElse(false)
            || annotationMetadata.classValue(SerdeConfig.class, SerdeConfig.SERIALIZER_CLASS).isPresent()
            || annotationMetadata.classValue(SerdeConfig.class, SerdeConfig.DESERIALIZER_CLASS).isPresent();
    }
}
