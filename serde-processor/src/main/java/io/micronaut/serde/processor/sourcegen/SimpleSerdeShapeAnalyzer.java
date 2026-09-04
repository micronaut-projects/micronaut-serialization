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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.EnumConstantElement;
import io.micronaut.inject.ast.EnumElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.serde.FormatConfiguration;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.annotation.SerdeableGenerated;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;
import io.micronaut.serde.util.SerdePropertyAccess;

import java.lang.annotation.Annotation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Analyzes candidate types and decides source-generation eligibility and fallback reasons.
 */
public final class SimpleSerdeShapeAnalyzer {
    private static final String SERDEABLE_SERIALIZABLE = Serdeable.Serializable.class.getName();
    private static final String SERDEABLE_DESERIALIZABLE = Serdeable.Deserializable.class.getName();
    private static final String JACKSON_ANNOTATION_PREFIX = "com.fasterxml.jackson.annotation.";
    private static final String BSON_REPRESENTATION = "org.bson.codecs.pojo.annotations.BsonRepresentation";
    private static final String JACKSON_DATAFORMAT = "tools.jackson.dataformat.";
    private static final String JACKSON_XML_PROPERTY =
        "tools.jackson.dataformat.xml.annotation.JacksonXmlProperty";
    private static final String JACKSON_XML_ELEMENT_WRAPPER =
        "tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper";
    private static final String JACKSON_XML_TEXT =
        "tools.jackson.dataformat.xml.annotation.JacksonXmlText";
    private static final String JACKSON_XML_CDATA =
        "tools.jackson.dataformat.xml.annotation.JacksonXmlCData";
    private static final String JAXB_XML_ELEMENT = "jakarta.xml.bind.annotation.XmlElement";
    private static final String JAXB_XML_ATTRIBUTE = "jakarta.xml.bind.annotation.XmlAttribute";
    private static final String JAXB_XML_MIXED = "jakarta.xml.bind.annotation.XmlMixed";
    private static final String JAXB_XML_ACCESSOR_TYPE = "jakarta.xml.bind.annotation.XmlAccessorType";

    @SuppressWarnings("java:S3776")
    public SimpleSerdeShapeDecision analyze(ClassElement element) {
        LinkedHashMap<SimpleSerdeShapeDecision.FallbackReason, String> serializerReasons = new LinkedHashMap<>();
        LinkedHashMap<SimpleSerdeShapeDecision.FallbackReason, String> deserializerReasons = new LinkedHashMap<>();
        SimpleSerdeShapeDecision.ShapeKind shapeKind = resolveShapeKind(element);
        boolean generated = element.hasAnnotation(SerdeableGenerated.class);
        if (isSerializerSkipped(element)) {
            failSerializer(serializerReasons, SimpleSerdeShapeDecision.FallbackReason.SOURCEGEN_SKIPPED);
        }
        if (isDeserializerSkipped(element)) {
            failDeserializer(deserializerReasons, SimpleSerdeShapeDecision.FallbackReason.SOURCEGEN_SKIPPED);
        }

        if (shapeKind == SimpleSerdeShapeDecision.ShapeKind.UNSUPPORTED) {
            failBoth(serializerReasons, deserializerReasons, SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
            return decision(shapeKind, serializerReasons, deserializerReasons);
        }

        if (shapeKind == SimpleSerdeShapeDecision.ShapeKind.ENUM) {
            var unsupportedAnnotations = unsupportedJacksonAnnotationsOnEnum(element);
            if (!unsupportedAnnotations.isEmpty()
                && failBoth(
                    serializerReasons,
                    deserializerReasons,
                    SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_ANNOTATIONS,
                    unsupportedAnnotationsMessage(unsupportedAnnotations)
                )) {
                return decision(shapeKind, serializerReasons, deserializerReasons);
            }
        }

        if (element.hasDeclaredAnnotation(SerdeConfig.SerSubtyped.class)
            && failBoth(serializerReasons, deserializerReasons, SimpleSerdeShapeDecision.FallbackReason.SUBTYPED)) {
            return decision(shapeKind, serializerReasons, deserializerReasons);
        }
        if (!isBothFailed(serializerReasons, deserializerReasons)
            && hasSubtypedPropertyTypes(element)
            && failBoth(serializerReasons, deserializerReasons, SimpleSerdeShapeDecision.FallbackReason.SUBTYPED)) {
            return decision(shapeKind, serializerReasons, deserializerReasons);
        }
        if (!isBothFailed(serializerReasons, deserializerReasons)
            && hasAnnotation(element, SerdeConfig.SerUnwrapped.class)
            && failBoth(serializerReasons, deserializerReasons, SimpleSerdeShapeDecision.FallbackReason.UNWRAPPED)) {
            return decision(shapeKind, serializerReasons, deserializerReasons);
        }
        if (!isBothFailed(serializerReasons, deserializerReasons)
            && (usesDocumentIds(element)
                || hasAnnotation(element, JAXB_XML_MIXED)
                || element.hasDeclaredAnnotation(JAXB_XML_ACCESSOR_TYPE))
            && failBoth(serializerReasons, deserializerReasons, SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE)) {
            return decision(shapeKind, serializerReasons, deserializerReasons);
        }
        if (serializerReasons.isEmpty() && hasAnnotation(element, SerdeConfig.SerAnyGetter.class)) {
            failSerializer(serializerReasons, SimpleSerdeShapeDecision.FallbackReason.ANY_GETTER);
            if (isBothFailed(serializerReasons, deserializerReasons)) {
                return decision(shapeKind, serializerReasons, deserializerReasons);
            }
        }
        if (deserializerReasons.isEmpty() && hasAnnotation(element, SerdeConfig.SerAnySetter.class)) {
            failDeserializer(deserializerReasons, SimpleSerdeShapeDecision.FallbackReason.ANY_SETTER);
            if (isBothFailed(serializerReasons, deserializerReasons)) {
                return decision(shapeKind, serializerReasons, deserializerReasons);
            }
        }
        // A type deserialized through a builder is handled by the introspection-backed deserializer,
        // which owns the builder semantics such as required properties and declared default values.
        if (deserializerReasons.isEmpty() && hasIntrospectionBuilder(element)) {
            failDeserializer(deserializerReasons, SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
            if (isBothFailed(serializerReasons, deserializerReasons)) {
                return decision(shapeKind, serializerReasons, deserializerReasons);
            }
        }
        if (!isBothFailed(serializerReasons, deserializerReasons)
            && hasIncludeConfig(element)
            && failBoth(serializerReasons, deserializerReasons, SimpleSerdeShapeDecision.FallbackReason.INCLUDE)) {
            return decision(shapeKind, serializerReasons, deserializerReasons);
        }
        if (!isBothFailed(serializerReasons, deserializerReasons)
            && hasPropertyOrderConfig(element)
            && failBoth(serializerReasons, deserializerReasons, SimpleSerdeShapeDecision.FallbackReason.PROPERTY_ORDER)) {
            return decision(shapeKind, serializerReasons, deserializerReasons);
        }
        var unsupportedAnnotations = unsupportedJacksonAnnotations(element);
        if (serializerReasons.isEmpty() && deserializerReasons.isEmpty()
            && !unsupportedAnnotations.isEmpty()
            && failBoth(
                serializerReasons,
                deserializerReasons,
                SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_ANNOTATIONS,
                unsupportedAnnotationsMessage(unsupportedAnnotations)
            )) {
            return decision(shapeKind, serializerReasons, deserializerReasons);
        }
        if (!isBothFailed(serializerReasons, deserializerReasons)
            && hasUnsupportedSerdeConfigMetadata(element.getAnnotationMetadata())
            && failBoth(serializerReasons, deserializerReasons, SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE)) {
            return decision(shapeKind, serializerReasons, deserializerReasons);
        }
        if (!isBothFailed(serializerReasons, deserializerReasons)
            && (element.hasAnnotation(SerdeConfig.SerIgnored.class)
            || hasAnnotation(element, SerdeConfig.SerIgnored.class)
            || element.hasDeclaredAnnotation(SerdeConfig.SerIgnored.class))
            && failBoth(serializerReasons, deserializerReasons, SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE)) {
            return decision(shapeKind, serializerReasons, deserializerReasons);
        }
        if (!isBothFailed(serializerReasons, deserializerReasons)
            && hasPropertyNamedIgnored(element)
            && failBoth(serializerReasons, deserializerReasons, SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE)) {
            return decision(shapeKind, serializerReasons, deserializerReasons);
        }
        if (!hasIncludeConfig(element)
            && (element.hasAnnotation(SerdeConfig.SerIncluded.class)
            || hasAnnotation(element, SerdeConfig.SerIncluded.class)
            || element.hasDeclaredAnnotation(SerdeConfig.SerIncluded.class))
            && failBoth(serializerReasons, deserializerReasons, SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE)) {
            return decision(shapeKind, serializerReasons, deserializerReasons);
        }
        if (!generated
            && serializerReasons.isEmpty() && deserializerReasons.isEmpty()
            && hasPotentialGlobalOrderingConflict(element)
            && failBoth(serializerReasons, deserializerReasons, SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE)) {
            return decision(shapeKind, serializerReasons, deserializerReasons);
        }
        if (!element.isEnum()
            && !isBothFailed(serializerReasons, deserializerReasons)
            && hasAnnotation(element, SerdeConfig.SerValue.class)
            && failBoth(serializerReasons, deserializerReasons, SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE)) {
            return decision(shapeKind, serializerReasons, deserializerReasons);
        }
        if (serializerReasons.isEmpty()
            && hasAnnotation(element, SerdeConfig.SerKey.class)) {
            failSerializer(serializerReasons, SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
            if (isBothFailed(serializerReasons, deserializerReasons)) {
                return decision(shapeKind, serializerReasons, deserializerReasons);
            }
        }
        if (!element.isEnum()
            && !isBothFailed(serializerReasons, deserializerReasons)
            && hasSerValueInPropertyTypes(element)
            && failBoth(serializerReasons, deserializerReasons, SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE)) {
            return decision(shapeKind, serializerReasons, deserializerReasons);
        }
        if (!isBothFailed(serializerReasons, deserializerReasons)
            && hasCustomSerdeClassOverride(element)
            && failBoth(serializerReasons, deserializerReasons, SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE)) {
            return decision(shapeKind, serializerReasons, deserializerReasons);
        }
        if (!isBothFailed(serializerReasons, deserializerReasons)
            && hasCustomNaming(element)
            && failBoth(serializerReasons, deserializerReasons, SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE)) {
            return decision(shapeKind, serializerReasons, deserializerReasons);
        }
        if (!generated
            && serializerReasons.isEmpty() && deserializerReasons.isEmpty()
            && hasPotentialGlobalNamingConflict(element)
            && failBoth(serializerReasons, deserializerReasons, SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE)) {
            return decision(shapeKind, serializerReasons, deserializerReasons);
        }
        if (serializerReasons.isEmpty() && hasSerializeAsOverride(element)) {
            failSerializer(serializerReasons, SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
            if (isBothFailed(serializerReasons, deserializerReasons)) {
                return decision(shapeKind, serializerReasons, deserializerReasons);
            }
        }
        if (deserializerReasons.isEmpty() && hasDeserializeAsOverride(element)) {
            failDeserializer(deserializerReasons, SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
            if (isBothFailed(serializerReasons, deserializerReasons)) {
                return decision(shapeKind, serializerReasons, deserializerReasons);
            }
        }
        if (serializerReasons.isEmpty() && hasPropertyLevelSerializableOverride(element)) {
            failSerializer(serializerReasons, SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
            if (isBothFailed(serializerReasons, deserializerReasons)) {
                return decision(shapeKind, serializerReasons, deserializerReasons);
            }
        }
        if (deserializerReasons.isEmpty() && hasPropertyLevelDeserializableOverride(element)) {
            failDeserializer(deserializerReasons, SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE);
            if (isBothFailed(serializerReasons, deserializerReasons)) {
                return decision(shapeKind, serializerReasons, deserializerReasons);
            }
        }
        SerdeConfig.SerCreatorMode creatorMode = element.getPrimaryConstructor()
            .flatMap(c -> c.enumValue(Creator.class, "mode", SerdeConfig.SerCreatorMode.class))
            .orElse(SerdeConfig.SerCreatorMode.PROPERTIES);
        if (creatorMode == SerdeConfig.SerCreatorMode.DELEGATING
            && failBoth(serializerReasons, deserializerReasons, SimpleSerdeShapeDecision.FallbackReason.COMPLEX_CREATOR)) {
            return decision(shapeKind, serializerReasons, deserializerReasons);
        }
        if (!isBothFailed(serializerReasons, deserializerReasons)
            && hasDirectIterableProperties(element)
            && failBoth(serializerReasons, deserializerReasons, SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE)) {
            return decision(shapeKind, serializerReasons, deserializerReasons);
        }
        if (!isBothFailed(serializerReasons, deserializerReasons)
            && hasCustomPropertyNames(element)
            && failBoth(serializerReasons, deserializerReasons, SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE)) {
            return decision(shapeKind, serializerReasons, deserializerReasons);
        }
        if (!isBothFailed(serializerReasons, deserializerReasons)
            && hasUnsupportedPropertySerdeConfig(element)
            && failBoth(serializerReasons, deserializerReasons, SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE)) {
            return decision(shapeKind, serializerReasons, deserializerReasons);
        }
        if (!isBothFailed(serializerReasons, deserializerReasons)
            && hasAnnotation(element, BSON_REPRESENTATION)
            && failBoth(serializerReasons, deserializerReasons, SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_SHAPE)) {
            return decision(shapeKind, serializerReasons, deserializerReasons);
        }
        if (shapeKind == SimpleSerdeShapeDecision.ShapeKind.ENUM
            && !isBothFailed(serializerReasons, deserializerReasons)
            && hasComplexEnumCustomization(element)
            && failBoth(serializerReasons, deserializerReasons, SimpleSerdeShapeDecision.FallbackReason.COMPLEX_ENUM)) {
            return decision(shapeKind, serializerReasons, deserializerReasons);
        }
        return decision(shapeKind, serializerReasons, deserializerReasons);
    }

    private SimpleSerdeShapeDecision decision(SimpleSerdeShapeDecision.ShapeKind shapeKind,
                                              Map<SimpleSerdeShapeDecision.FallbackReason, String> serializerReasons,
                                              Map<SimpleSerdeShapeDecision.FallbackReason, String> deserializerReasons) {
        return new SimpleSerdeShapeDecision(
            shapeKind,
            serializerReasons.isEmpty(),
            deserializerReasons.isEmpty(),
            serializerReasons,
            deserializerReasons
        );
    }

    private boolean isBothFailed(Map<SimpleSerdeShapeDecision.FallbackReason, String> serializerReasons,
                                 Map<SimpleSerdeShapeDecision.FallbackReason, String> deserializerReasons) {
        return !serializerReasons.isEmpty() && !deserializerReasons.isEmpty();
    }

    private boolean failBoth(Map<SimpleSerdeShapeDecision.FallbackReason, String> serializerReasons,
                             Map<SimpleSerdeShapeDecision.FallbackReason, String> deserializerReasons,
                             SimpleSerdeShapeDecision.FallbackReason reason) {
        return failBoth(serializerReasons, deserializerReasons, reason, reason.message());
    }

    private boolean failBoth(Map<SimpleSerdeShapeDecision.FallbackReason, String> serializerReasons,
                             Map<SimpleSerdeShapeDecision.FallbackReason, String> deserializerReasons,
                             SimpleSerdeShapeDecision.FallbackReason reason,
                             String message) {
        failSerializer(serializerReasons, reason, message);
        failDeserializer(deserializerReasons, reason, message);
        return isBothFailed(serializerReasons, deserializerReasons);
    }

    private void failSerializer(Map<SimpleSerdeShapeDecision.FallbackReason, String> serializerReasons,
                                SimpleSerdeShapeDecision.FallbackReason reason) {
        failSerializer(serializerReasons, reason, reason.message());
    }

    private void failSerializer(Map<SimpleSerdeShapeDecision.FallbackReason, String> serializerReasons,
                                SimpleSerdeShapeDecision.FallbackReason reason,
                                String message) {
        if (serializerReasons.isEmpty()) {
            serializerReasons.put(reason, message);
        }
    }

    private void failDeserializer(Map<SimpleSerdeShapeDecision.FallbackReason, String> deserializerReasons,
                                  SimpleSerdeShapeDecision.FallbackReason reason) {
        failDeserializer(deserializerReasons, reason, reason.message());
    }

    private void failDeserializer(Map<SimpleSerdeShapeDecision.FallbackReason, String> deserializerReasons,
                                  SimpleSerdeShapeDecision.FallbackReason reason,
                                  String message) {
        if (deserializerReasons.isEmpty()) {
            deserializerReasons.put(reason, message);
        }
    }

    private boolean hasAnnotation(ClassElement element, Class<? extends Annotation> annotation) {
        if (element.getPrimaryConstructor().map(c -> hasAnnotation(c, annotation)).orElse(false)) {
            return true;
        }
        if (element.getBeanProperties().stream().anyMatch(p -> p.hasAnnotation(annotation) || p.hasDeclaredAnnotation(annotation))) {
            return true;
        }
        if (!element.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyInstance().onlyDeclared()
            .annotated(a -> a.hasAnnotation(annotation) || a.hasDeclaredAnnotation(annotation))).isEmpty()) {
            return true;
        }
        return !element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyInstance().onlyDeclared()
            .annotated(a -> a.hasAnnotation(annotation) || a.hasDeclaredAnnotation(annotation))).isEmpty();
    }

    private boolean hasAnnotation(ClassElement element, String annotationName) {
        if (element.getPrimaryConstructor().map(c -> hasAnnotation(c, annotationName)).orElse(false)) {
            return true;
        }
        if (element.getBeanProperties().stream().anyMatch(p -> p.hasAnnotation(annotationName) || p.hasDeclaredAnnotation(annotationName))) {
            return true;
        }
        if (!element.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyInstance().onlyDeclared()
            .annotated(a -> a.hasAnnotation(annotationName) || a.hasDeclaredAnnotation(annotationName))).isEmpty()) {
            return true;
        }
        return !element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyInstance().onlyDeclared()
            .annotated(a -> a.hasAnnotation(annotationName) || a.hasDeclaredAnnotation(annotationName))).isEmpty();
    }

    private boolean hasAnnotationMetadata(ClassElement element, Predicate<AnnotationMetadata> predicate) {
        if (predicate.test(element.getAnnotationMetadata())) {
            return true;
        }
        if (element.getPrimaryConstructor().map(c -> hasAnnotationMetadata(c, predicate)).orElse(false)) {
            return true;
        }
        for (PropertyElement property : element.getBeanProperties()) {
            if (predicate.test(property.getAnnotationMetadata())
                || property.getReadMethod().map(method -> hasAnnotationMetadata(method, predicate)).orElse(false)
                || property.getWriteMethod().map(method -> hasAnnotationMetadata(method, predicate)).orElse(false)) {
                return true;
            }
        }
        for (Element field : element.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyInstance().onlyDeclared())) {
            if (predicate.test(field.getAnnotationMetadata())) {
                return true;
            }
        }
        for (MethodElement method : element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyInstance().onlyDeclared())) {
            if (hasAnnotationMetadata(method, predicate)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnnotationMetadata(MethodElement methodElement, Predicate<AnnotationMetadata> predicate) {
        if (predicate.test(methodElement.getAnnotationMetadata())) {
            return true;
        }
        for (ParameterElement parameter : methodElement.getParameters()) {
            if (predicate.test(parameter.getAnnotationMetadata())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasIncludeConfig(ClassElement element) {
        if (hasAnnotationMetadata(element, this::hasIncludeConfig)) {
            return true;
        }
        return element.getEnclosingType().map(this::hasIncludeConfig).orElse(false);
    }

    private boolean hasIncludeConfig(AnnotationMetadata annotationMetadata) {
        return annotationMetadata.enumValue(SerdeConfig.class, SerdeConfig.INCLUDE, SerdeConfig.SerInclude.class).isPresent()
            || annotationMetadata.enumValue(SerdeConfig.class, SerdeConfig.INCLUDE_CONTENT, SerdeConfig.SerInclude.class).isPresent();
    }

    private Map<String, Boolean> unsupportedJacksonAnnotations(ClassElement element) {
        var annotations = new LinkedHashMap<String, Boolean>();
        collectJacksonAnnotationsInTypeHierarchy(element, annotations, new LinkedHashMap<>());
        for (PropertyElement property : element.getBeanProperties()) {
            ClassElement serializationType = property.getReadMethod().map(MethodElement::getReturnType).orElse(property.getType());
            collectJacksonAnnotationsInTypeHierarchy(serializationType, annotations, new LinkedHashMap<>());
            ClassElement deserializationType = property.getWriteMethod().map(m -> m.getParameters()[0].getType()).orElse(property.getType());
            collectJacksonAnnotationsInTypeHierarchy(deserializationType, annotations, new LinkedHashMap<>());
            collectJacksonAnnotationNames(property.getAnnotationNames(), annotations);
            property.getReadMethod().ifPresent(method -> collectJacksonAnnotationNames(method.getAnnotationNames(), annotations));
            property.getWriteMethod().ifPresent(method -> collectJacksonAnnotationNames(method.getAnnotationNames(), annotations));
        }
        for (Element field : element.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyInstance().onlyDeclared())) {
            collectJacksonAnnotationNames(field.getAnnotationNames(), annotations);
        }
        for (MethodElement method : element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyInstance().onlyDeclared())) {
            collectJacksonAnnotationNames(method.getAnnotationNames(), annotations);
        }
        element.getEnclosingType().ifPresent(enclosing -> collectUnsupportedJacksonAnnotations(enclosing, annotations));
        return annotations;
    }

    private void collectUnsupportedJacksonAnnotations(ClassElement element,
                                                       Map<String, Boolean> annotations) {
        annotations.putAll(unsupportedJacksonAnnotations(element));
    }

    private void collectJacksonAnnotationNames(Set<String> annotationNames,
                                               Map<String, Boolean> annotations) {
        for (String annotationName : annotationNames) {
            if (isJacksonAnnotationName(annotationName) && !isSupportedXmlAnnotation(annotationName)) {
                annotations.putIfAbsent(displayAnnotationName(annotationName), Boolean.TRUE);
            }
        }
    }

    private boolean isSupportedXmlAnnotation(String annotationName) {
        return JACKSON_XML_PROPERTY.equals(annotationName)
            || JACKSON_XML_ELEMENT_WRAPPER.equals(annotationName)
            || JACKSON_XML_TEXT.equals(annotationName)
            || JACKSON_XML_CDATA.equals(annotationName);
    }

    private boolean isJacksonAnnotationName(String name) {
        return name.startsWith(JACKSON_ANNOTATION_PREFIX) || name.startsWith(JACKSON_DATAFORMAT);
    }

    private String displayAnnotationName(String annotationName) {
        int index = annotationName.lastIndexOf('.');
        String simpleName = index == -1 ? annotationName : annotationName.substring(index + 1);
        return "@" + simpleName;
    }

    private String unsupportedAnnotationsMessage(Map<String, Boolean> annotations) {
        return SimpleSerdeShapeDecision.FallbackReason.UNSUPPORTED_ANNOTATIONS.message() + ": " + String.join(", ", annotations.keySet());
    }

    private void collectJacksonAnnotationsInTypeHierarchy(ClassElement classElement,
                                                          Map<String, Boolean> annotations,
                                                          Map<String, Boolean> visited) {
        if (visited.putIfAbsent(classElement.getName(), Boolean.TRUE) != null) {
            return;
        }
        collectJacksonAnnotationNames(classElement.getAnnotationNames(), annotations);
        if (classElement.isEnum()) {
            annotations.putAll(unsupportedJacksonAnnotationsOnEnum(classElement));
        }
        for (ClassElement interfaceElement : classElement.getInterfaces()) {
            collectJacksonAnnotationsInTypeHierarchy(interfaceElement, annotations, visited);
        }
        classElement.getSuperType().ifPresent(superType -> collectJacksonAnnotationsInTypeHierarchy(superType, annotations, visited));
    }

    private static boolean hasIntrospectionBuilder(ClassElement element) {
        AnnotationValue<Introspected> introspected = element.getAnnotation(Introspected.class);
        if (introspected == null) {
            return false;
        }
        return introspected.annotationClassValue("builderClass").isPresent()
            || introspected.getAnnotation("builder", Introspected.IntrospectionBuilder.class)
            .flatMap(builder -> builder.annotationClassValue("builderClass"))
            .isPresent();
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
            if (!isSupportedBeanProperty(element, property)) {
                return false;
            }
        }
        return true;
    }

    private boolean isSupportedBeanProperty(ClassElement element,
                                            PropertyElement property) {
        ClassElement readType = property.getReadType().orElse(null);
        ClassElement writeType = property.getWriteType().orElse(null);
        return readType != null
            && writeType != null
            && !hasTypeVariable(readType)
            && !hasTypeVariable(writeType)
            && isSupportedReadAccess(element, property)
            && isSupportedWriteAccess(element, property);
    }

    private boolean hasTypeVariable(ClassElement type) {
        if (type.isTypeVariable()) {
            return true;
        }
        for (ClassElement typeArgument : type.getBoundGenericTypes()) {
            if (hasTypeVariable(typeArgument)) {
                return true;
            }
        }
        for (ClassElement typeArgument : type.getTypeArguments().values()) {
            if (hasTypeVariable(typeArgument)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSupportedReadAccess(ClassElement element, PropertyElement property) {
        if (property.getReadAccessKind() == PropertyElement.AccessKind.FIELD) {
            return property.getField()
                .filter(field -> field.isAccessible(element, false))
                .isPresent();
        }
        return property.getReadMethod().isPresent();
    }

    private boolean isSupportedWriteAccess(ClassElement element, PropertyElement property) {
        if (property.getWriteAccessKind() == PropertyElement.AccessKind.FIELD) {
            return property.getField()
                .filter(field -> !field.isFinal())
                .filter(field -> field.isAccessible(element, false))
                .isPresent();
        }
        return property.getWriteMethod().isPresent();
    }

    private boolean isSerializerSkipped(ClassElement element) {
        return element.booleanValue(SerdeableGenerated.class, "skip").orElse(false)
            || element.booleanValue(SerdeableGenerated.class, "skipSerializer").orElse(false);
    }

    private boolean isDeserializerSkipped(ClassElement element) {
        return element.booleanValue(SerdeableGenerated.class, "skip").orElse(false)
            || element.booleanValue(SerdeableGenerated.class, "skipDeserializer").orElse(false);
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

    private Map<String, Boolean> unsupportedJacksonAnnotationsOnEnum(ClassElement element) {
        var annotations = new LinkedHashMap<String, Boolean>();
        if (!element.isEnum()) {
            return annotations;
        }
        collectJacksonAnnotationNames(element.getAnnotationNames(), annotations);
        for (EnumConstantElement enumConstant : ((EnumElement) element).elements()) {
            collectJacksonAnnotationNames(enumConstant.getAnnotationNames(), annotations);
        }
        for (Element field : element.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyDeclared())) {
            collectJacksonAnnotationNames(field.getAnnotationNames(), annotations);
        }
        for (MethodElement method : element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared())) {
            collectJacksonAnnotationNames(method.getAnnotationNames(), annotations);
        }
        return annotations;
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
            if (configured != null
                && !configured.equals(property.getName())
                && !hasSupportedXmlPropertyAnnotation(property)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSupportedXmlPropertyAnnotation(PropertyElement property) {
        return property.hasAnnotation(JACKSON_XML_PROPERTY)
            || property.hasAnnotation(JAXB_XML_ELEMENT)
            || property.hasAnnotation(JAXB_XML_ATTRIBUTE)
            || property.getReadMethod().map(method -> method.hasAnnotation(JACKSON_XML_PROPERTY)).orElse(false)
            || property.getReadMethod().map(method -> method.hasAnnotation(JAXB_XML_ELEMENT) || method.hasAnnotation(JAXB_XML_ATTRIBUTE)).orElse(false)
            || property.getWriteMethod().map(method -> method.hasAnnotation(JACKSON_XML_PROPERTY)).orElse(false)
            || property.getWriteMethod().map(method -> method.hasAnnotation(JAXB_XML_ELEMENT) || method.hasAnnotation(JAXB_XML_ATTRIBUTE)).orElse(false)
            || property.getField().map(field -> field.hasAnnotation(JACKSON_XML_PROPERTY) || field.hasAnnotation(JAXB_XML_ELEMENT) || field.hasAnnotation(JAXB_XML_ATTRIBUTE)).orElse(false);
    }

    private boolean usesDocumentIds(ClassElement element) {
        return hasAnnotationMetadata(element, annotationMetadata -> annotationMetadata.enumValue(SerdeConfig.SerManagedRef.class,
                SerdeConfig.SerManagedRef.SCOPE, SerdeConfig.SerManagedRef.Scope.class).orElse(null) == SerdeConfig.SerManagedRef.Scope.DOCUMENT
            || annotationMetadata.enumValue(SerdeConfig.class, SerdeConfig.ID_REFERENCE, SerdeConfig.IdReference.class).isPresent());
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
        return hasAnnotationMetadata(element, this::hasPropertyOrderConfig);
    }

    private boolean hasPropertyOrderConfig(AnnotationMetadata annotationMetadata) {
        return annotationMetadata.isAnnotationPresent(SerdeConfig.META_ANNOTATION_PROPERTY_ORDER)
            || annotationMetadata.stringValues(SerdeConfig.META_ANNOTATION_PROPERTY_ORDER).length > 0;
    }

    private boolean hasSerValueInPropertyTypes(ClassElement element) {
        for (PropertyElement property : element.getBeanProperties()) {
            ClassElement serializationType = property.getReadMethod().map(MethodElement::getReturnType).orElse(property.getType());
            if (hasAnnotation(serializationType, SerdeConfig.SerValue.class)) {
                return true;
            }
            ClassElement deserializationType = property.getWriteMethod().map(m -> m.getParameters()[0].getType()).orElse(property.getType());
            if (hasAnnotation(deserializationType, SerdeConfig.SerValue.class)) {
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
            || SerdePropertyAccess.hasRestrictedAccess(element.getAnnotationMetadata())
            || element.booleanValue(SerdeConfig.class, SerdeConfig.MERGE).orElse(false)
            || FormatConfiguration.from(element.getAnnotationMetadata()) != null
            || hasFeatureOverrides(element.getAnnotationMetadata())
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
            || SerdePropertyAccess.hasRestrictedAccess(annotationMetadata)
            || annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.MERGE).orElse(false)
            || FormatConfiguration.from(annotationMetadata) != null
            || hasFeatureOverrides(annotationMetadata)
            || hasSerializeAsOverride(annotationMetadata)
            || hasDeserializeAsOverride(annotationMetadata)
            || hasCustomNaming(annotationMetadata)
            || annotationMetadata.classValue(SerdeConfig.class, SerdeConfig.SERIALIZER_CLASS).isPresent()
            || annotationMetadata.classValue(SerdeConfig.class, SerdeConfig.DESERIALIZER_CLASS).isPresent();
    }

    private boolean hasFeatureOverrides(AnnotationMetadata annotationMetadata) {
        return annotationMetadata.stringValues(SerdeConfig.class, SerdeConfig.FEATURES_WITH).length > 0
            || annotationMetadata.stringValues(SerdeConfig.class, SerdeConfig.FEATURES_WITHOUT).length > 0;
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
        if (methodElement.hasAnnotation(annotation) || methodElement.hasDeclaredAnnotation(annotation)) {
            return true;
        }
        for (ParameterElement parameter : methodElement.getParameters()) {
            if (parameter.hasAnnotation(annotation) || parameter.hasDeclaredAnnotation(annotation)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasAnnotation(MethodElement methodElement, String annotationName) {
        if (methodElement.hasAnnotation(annotationName) || methodElement.hasDeclaredAnnotation(annotationName)) {
            return true;
        }
        for (ParameterElement parameter : methodElement.getParameters()) {
            if (parameter.hasAnnotation(annotationName) || parameter.hasDeclaredAnnotation(annotationName)) {
                return true;
            }
        }
        return false;
    }

}
