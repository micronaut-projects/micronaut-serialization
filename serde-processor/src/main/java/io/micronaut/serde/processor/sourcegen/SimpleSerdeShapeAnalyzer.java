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
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.FormatConfiguration;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.annotation.SerdeableGenerated;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.processor.sourcegen.SimpleSerdeShapeDecision.FallbackReason;
import io.micronaut.serde.processor.sourcegen.SimpleSerdeShapeDecision.ShapeKind;
import io.micronaut.serde.processor.sourcegen.beans.BeanSerdeShapeResolver;
import io.micronaut.serde.processor.sourcegen.records.RecordSerdeShapeResolver;
import io.micronaut.serde.util.SerdePropertyAccess;

import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.HashSet;
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
    private static final String DEFAULT_SERIALIZER_CLASS = Serializer.class.getName();
    private static final String DEFAULT_DESERIALIZER_CLASS = Deserializer.class.getName();
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
    private static final String JAXB_XML_MIXED = "jakarta.xml.bind.annotation.XmlMixed";
    private static final String JAXB_XML_ACCESSOR_TYPE = "jakarta.xml.bind.annotation.XmlAccessorType";
    private static final String JAXB_XML_ROOT_ELEMENT = "jakarta.xml.bind.annotation.XmlRootElement";
    private static final String BINDABLE = "io.micronaut.core.bind.annotation.Bindable";
    private static final String BINDABLE_DEFAULT_VALUE = "defaultValue";
    /**
     * Jackson annotations whose mapped {@link SerdeConfig} members the generated bean and record serdes
     * honor completely. Members that are not supported are detected through the mapped configuration.
     */
    private static final Set<String> SUPPORTED_JACKSON_ANNOTATIONS = Set.of(
        JACKSON_ANNOTATION_PREFIX + "JsonProperty",
        JACKSON_ANNOTATION_PREFIX + "JsonIgnore",
        JACKSON_ANNOTATION_PREFIX + "JsonIgnoreProperties",
        JACKSON_ANNOTATION_PREFIX + "JsonIgnoreType",
        JACKSON_ANNOTATION_PREFIX + "JsonIncludeProperties",
        JACKSON_ANNOTATION_PREFIX + "JsonPropertyOrder",
        JACKSON_ANNOTATION_PREFIX + "JsonInclude",
        JACKSON_ANNOTATION_PREFIX + "JsonAlias",
        JACKSON_ANNOTATION_PREFIX + "JsonClassDescription",
        JACKSON_ANNOTATION_PREFIX + "JsonPropertyDescription",
        JACKSON_XML_PROPERTY,
        JACKSON_XML_ELEMENT_WRAPPER,
        JACKSON_XML_TEXT,
        JACKSON_XML_CDATA
    );
    /**
     * Generated enum serdes only understand the XML annotations; every other Jackson annotation on an enum
     * routes to the runtime enum serde.
     */
    private static final Set<String> SUPPORTED_ENUM_JACKSON_ANNOTATIONS = Set.of(
        JACKSON_XML_PROPERTY,
        JACKSON_XML_ELEMENT_WRAPPER,
        JACKSON_XML_TEXT,
        JACKSON_XML_CDATA
    );

    @SuppressWarnings("java:S3776")
    /**
     * The eligibility checks in the order they apply. Each phase reports whether the analysis is
     * complete, which is the case once both directions fell back to the runtime serdes.
     */
    private final List<Predicate<Analysis>> phases = List.of(
        this::analyzeShapeKind,
        this::analyzeTypeStructure,
        this::analyzeAccessors,
        this::analyzeTypeConfiguration,
        this::analyzeValueOverrides,
        this::analyzeSerdeAsOverrides,
        this::analyzePropertyShapes
    );
    private @Nullable ClassElement analyzedElement;
    private @Nullable List<PropertyElement> analyzedProperties;

    /**
     * The properties as the introspection resolves them, so that every eligibility check looks at
     * the same property set the shape resolvers and the generated code are built from. The list is
     * kept for the type being analyzed since the checks read it many times.
     */
    private List<PropertyElement> beanProperties(ClassElement element) {
        List<PropertyElement> properties = analyzedProperties;
        if (analyzedElement != element || properties == null) {
            properties = BeanSerdeShapeResolver.introspectedProperties(element);
            analyzedElement = element;
            analyzedProperties = properties;
        }
        return properties;
    }

    public SimpleSerdeShapeDecision analyze(ClassElement element) {
        Analysis analysis = new Analysis(element, resolveShapeKind(element));
        analyzeStereotypes(analysis);
        for (Predicate<Analysis> phase : phases) {
            if (phase.test(analysis)) {
                break;
            }
        }
        return analysis.decision();
    }

    private void analyzeStereotypes(Analysis analysis) {
        ClassElement element = analysis.element;
        if (isSerializerSkipped(element)) {
            analysis.failSerializer(FallbackReason.SOURCEGEN_SKIPPED);
        }
        if (isDeserializerSkipped(element)) {
            analysis.failDeserializer(FallbackReason.SOURCEGEN_SKIPPED);
        }
        // A type declared serializable only, such as a serialize-only import, has no deserializable
        // introspection; a generated deserializer would bypass that contract.
        if (!element.hasStereotype(Serdeable.Serializable.class) && !element.hasStereotype(Serdeable.class)) {
            analysis.failSerializer(FallbackReason.SOURCEGEN_SKIPPED);
        }
        if (!element.hasStereotype(Serdeable.Deserializable.class) && !element.hasStereotype(Serdeable.class)) {
            analysis.failDeserializer(FallbackReason.SOURCEGEN_SKIPPED);
        }
    }

    /**
     * Checks that depend on the shape kind alone.
     *
     * @return {@code true} once the analysis is complete
     */
    private boolean analyzeShapeKind(Analysis analysis) {
        ClassElement element = analysis.element;
        if (analysis.shapeKind == ShapeKind.UNSUPPORTED) {
            analysis.failBoth(FallbackReason.UNSUPPORTED_SHAPE);
            return true;
        }
        if (analysis.shapeKind == ShapeKind.DEFAULT_CONSTRUCTOR_BEAN) {
            if (analysis.serializerOpen() && hasUnsupportedSerializedBeanProperty(element)) {
                analysis.failSerializer(FallbackReason.UNSUPPORTED_SHAPE);
            }
            if (analysis.deserializerOpen() && hasUnsupportedDeserializedBeanProperty(element)) {
                analysis.failDeserializer(FallbackReason.UNSUPPORTED_SHAPE);
            }
            if (analysis.bothFailed()) {
                return true;
            }
        }
        if (analysis.shapeKind == ShapeKind.ENUM) {
            var unsupportedAnnotations = unsupportedJacksonAnnotationsOnEnum(element);
            return !unsupportedAnnotations.isEmpty()
                && analysis.failBoth(FallbackReason.UNSUPPORTED_ANNOTATIONS, unsupportedAnnotationsMessage(unsupportedAnnotations));
        }
        return false;
    }

    /**
     * Type declarations the generated serdes cannot represent.
     *
     * @return {@code true} once the analysis is complete
     */
    private boolean analyzeTypeStructure(Analysis analysis) {
        ClassElement element = analysis.element;
        // A subtype declaration on the type or on a supertype means the runtime serdes write and
        // resolve the discriminator; the generated serdes know nothing about it.
        if ((element.hasDeclaredAnnotation(SerdeConfig.SerSubtyped.class) || hasSubtypedSupertype(element))
            && analysis.failBoth(FallbackReason.SUBTYPED)) {
            return true;
        }
        if (analysis.open() && hasSubtypedPropertyTypes(element) && analysis.failBoth(FallbackReason.SUBTYPED)) {
            return true;
        }
        if (analysis.open() && hasAnnotation(element, SerdeConfig.SerUnwrapped.class) && analysis.failBoth(FallbackReason.UNWRAPPED)) {
            return true;
        }
        return analysis.open()
            && (usesDocumentIds(element)
                || hasAnnotation(element, JAXB_XML_MIXED)
                || element.hasDeclaredAnnotation(JAXB_XML_ACCESSOR_TYPE)
                || hasXmlRootElement(element))
            && analysis.failBoth(FallbackReason.UNSUPPORTED_SHAPE);
    }

    /**
     * Accessors, builders and type-level inclusion and ordering the runtime serdes own.
     *
     * @return {@code true} once the analysis is complete
     */
    private boolean analyzeAccessors(Analysis analysis) {
        ClassElement element = analysis.element;
        if (analysis.serializerOpen() && hasAnnotation(element, SerdeConfig.SerAnyGetter.class) && analysis.failSerializer(FallbackReason.ANY_GETTER)) {
            return true;
        }
        if (analysis.deserializerOpen() && hasAnnotation(element, SerdeConfig.SerAnySetter.class) && analysis.failDeserializer(FallbackReason.ANY_SETTER)) {
            return true;
        }
        // A type deserialized through a builder is handled by the introspection-backed deserializer,
        // which owns the builder semantics such as required properties and declared default values.
        if (analysis.deserializerOpen() && hasIntrospectionBuilder(element) && analysis.failDeserializer(FallbackReason.UNSUPPORTED_SHAPE)) {
            return true;
        }
        // A property inclusion declared on the type, a property or the package is applied at build time by
        // the generated bean and record serializers. A content inclusion is applied by the value serializer
        // through the property argument, which only the runtime serializer carries.
        if (analysis.open() && hasUnsupportedInclude(analysis) && analysis.failBoth(FallbackReason.INCLUDE)) {
            return true;
        }
        // A type-level property order is applied by the generated serializer; an order declared on a
        // member configures the nested value, which only the runtime serializer applies.
        return analysis.open()
            && hasDeclaredMemberAnnotation(element, SerdeConfig.META_ANNOTATION_PROPERTY_ORDER)
            && analysis.failBoth(FallbackReason.PROPERTY_ORDER);
    }

    private boolean hasUnsupportedInclude(Analysis analysis) {
        return analysis.shapeKind == ShapeKind.ENUM ? hasIncludeConfig(analysis.element) : hasContentIncludeConfig(analysis.element);
    }

    /**
     * Annotations and serde configuration declared on the type.
     *
     * @return {@code true} once the analysis is complete
     */
    private boolean analyzeTypeConfiguration(Analysis analysis) {
        ClassElement element = analysis.element;
        var unsupportedAnnotations = unsupportedJacksonAnnotations(element);
        if (analysis.serializerOpen() && analysis.deserializerOpen()
            && !unsupportedAnnotations.isEmpty()
            && analysis.failBoth(FallbackReason.UNSUPPORTED_ANNOTATIONS, unsupportedAnnotationsMessage(unsupportedAnnotations))) {
            return true;
        }
        // Generated bean serdes honor ignored, read-only and write-only properties; the record
        // generators still hand those shapes to the runtime serde.
        boolean propertyExclusionSupported = analysis.propertyExclusionSupported();
        if (analysis.open()
            && hasUnsupportedSerdeConfigMetadata(element.getAnnotationMetadata(), propertyExclusionSupported)
            && analysis.failBoth(FallbackReason.UNSUPPORTED_SHAPE)) {
            return true;
        }
        if (analysis.open() && hasUnsupportedIgnoredConfig(element, propertyExclusionSupported) && analysis.failBoth(FallbackReason.UNSUPPORTED_SHAPE)) {
            return true;
        }
        if (analysis.open() && hasPropertyNamedIgnored(element) && analysis.failBoth(FallbackReason.UNSUPPORTED_SHAPE)) {
            return true;
        }
        return analysis.open()
            && hasUnsupportedIncludedConfig(element, propertyExclusionSupported)
            && analysis.failBoth(FallbackReason.UNSUPPORTED_SHAPE);
    }

    /**
     * Value, key, custom serde and naming overrides only the runtime serdes apply.
     *
     * @return {@code true} once the analysis is complete
     */
    private boolean analyzeValueOverrides(Analysis analysis) {
        ClassElement element = analysis.element;
        if (!element.isEnum() && analysis.open() && hasAnnotation(element, SerdeConfig.SerValue.class) && analysis.failBoth(FallbackReason.UNSUPPORTED_SHAPE)) {
            return true;
        }
        if (analysis.serializerOpen() && hasAnnotation(element, SerdeConfig.SerKey.class) && analysis.failSerializer(FallbackReason.UNSUPPORTED_SHAPE)) {
            return true;
        }
        if (!element.isEnum() && analysis.open() && hasSerValueInPropertyTypes(element) && analysis.failBoth(FallbackReason.UNSUPPORTED_SHAPE)) {
            return true;
        }
        if (analysis.open() && hasCustomSerdeClassOverride(element) && analysis.failBoth(FallbackReason.UNSUPPORTED_SHAPE)) {
            return true;
        }
        return analysis.open() && hasCustomNaming(element) && analysis.failBoth(FallbackReason.UNSUPPORTED_SHAPE);
    }

    /**
     * Serialize-as and deserialize-as overrides on the type or its properties.
     *
     * @return {@code true} once the analysis is complete
     */
    private boolean analyzeSerdeAsOverrides(Analysis analysis) {
        ClassElement element = analysis.element;
        if (analysis.serializerOpen() && hasSerializeAsOverride(element) && analysis.failSerializer(FallbackReason.UNSUPPORTED_SHAPE)) {
            return true;
        }
        if (analysis.deserializerOpen() && hasDeserializeAsOverride(element) && analysis.failDeserializer(FallbackReason.UNSUPPORTED_SHAPE)) {
            return true;
        }
        if (analysis.serializerOpen() && hasPropertyLevelSerializableOverride(element) && analysis.failSerializer(FallbackReason.UNSUPPORTED_SHAPE)) {
            return true;
        }
        return analysis.deserializerOpen()
            && hasPropertyLevelDeserializableOverride(element)
            && analysis.failDeserializer(FallbackReason.UNSUPPORTED_SHAPE);
    }

    /**
     * The creator and the properties the generated serdes are built from.
     *
     * @return {@code true} once the analysis is complete
     */
    private boolean analyzePropertyShapes(Analysis analysis) {
        ClassElement element = analysis.element;
        SerdeConfig.SerCreatorMode creatorMode = element.getPrimaryConstructor()
            .flatMap(c -> c.enumValue(Creator.class, "mode", SerdeConfig.SerCreatorMode.class))
            .orElse(SerdeConfig.SerCreatorMode.PROPERTIES);
        if (creatorMode == SerdeConfig.SerCreatorMode.DELEGATING && analysis.failBoth(FallbackReason.COMPLEX_CREATOR)) {
            return true;
        }
        if (analysis.open() && hasDirectIterableProperties(element) && analysis.failBoth(FallbackReason.UNSUPPORTED_SHAPE)) {
            return true;
        }
        // Two properties serialized under one name cannot share a generated key
        if (analysis.open() && hasDuplicateSerializedNames(element) && analysis.failBoth(FallbackReason.UNSUPPORTED_SHAPE)) {
            return true;
        }
        if (analysis.open()
            && hasUnsupportedPropertySerdeConfig(element, analysis.propertyExclusionSupported(), analysis.shapeKind == ShapeKind.CONSTRUCTOR_BEAN)
            && analysis.failBoth(FallbackReason.UNSUPPORTED_SHAPE)) {
            return true;
        }
        if (analysis.open() && hasAnnotation(element, BSON_REPRESENTATION) && analysis.failBoth(FallbackReason.UNSUPPORTED_SHAPE)) {
            return true;
        }
        return analysis.shapeKind == ShapeKind.ENUM
            && analysis.open()
            && hasComplexEnumCustomization(element)
            && analysis.failBoth(FallbackReason.COMPLEX_ENUM);
    }

    private boolean hasAnnotation(ClassElement element, Class<? extends Annotation> annotation) {
        if (element.getPrimaryConstructor().map(c -> hasAnnotation(c, annotation)).orElse(false)) {
            return true;
        }
        if (beanProperties(element).stream().anyMatch(p -> p.hasAnnotation(annotation) || p.hasDeclaredAnnotation(annotation))) {
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
        if (beanProperties(element).stream().anyMatch(p -> p.hasAnnotation(annotationName) || p.hasDeclaredAnnotation(annotationName))) {
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
        for (PropertyElement property : beanProperties(element)) {
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

    private boolean hasContentIncludeConfig(ClassElement element) {
        if (hasAnnotationMetadata(element, this::hasContentIncludeConfig)) {
            return true;
        }
        return hasContentIncludeConfig(element.getPackage().getAnnotationMetadata());
    }

    private boolean hasContentIncludeConfig(AnnotationMetadata annotationMetadata) {
        return annotationMetadata.enumValue(SerdeConfig.class, SerdeConfig.INCLUDE_CONTENT, SerdeConfig.SerInclude.class)
            .filter(include -> include != SerdeConfig.SerInclude.ALWAYS)
            .isPresent();
    }

    private Map<String, Boolean> unsupportedJacksonAnnotations(ClassElement element) {
        var annotations = new LinkedHashMap<String, Boolean>();
        collectJacksonAnnotationsInTypeHierarchy(element, annotations, new LinkedHashMap<>());
        for (PropertyElement property : beanProperties(element)) {
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
        collectJacksonAnnotationNames(annotationNames, annotations, SUPPORTED_JACKSON_ANNOTATIONS);
    }

    private void collectJacksonAnnotationNames(Set<String> annotationNames,
                                               Map<String, Boolean> annotations,
                                               Set<String> supportedAnnotations) {
        for (String annotationName : annotationNames) {
            if (isJacksonAnnotationName(annotationName) && !supportedAnnotations.contains(annotationName)) {
                annotations.putIfAbsent(displayAnnotationName(annotationName), Boolean.TRUE);
            }
        }
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
        if (RecordSerdeShapeResolver.isConstructorBean(element)) {
            return SimpleSerdeShapeDecision.ShapeKind.CONSTRUCTOR_BEAN;
        }
        if (isDefaultConstructorBean(element)) {
            return SimpleSerdeShapeDecision.ShapeKind.DEFAULT_CONSTRUCTOR_BEAN;
        }
        return SimpleSerdeShapeDecision.ShapeKind.UNSUPPORTED;
    }

    private boolean isDefaultConstructorBean(ClassElement element) {
        if (element.isInterface() || element.isAbstract() || element.isEnum() || element.isRecord()) {
            return false;
        }
        boolean hasDefaultConstructor = element.getAccessibleConstructors().stream().anyMatch(c -> c.getParameters().length == 0);
        if (!hasDefaultConstructor) {
            return false;
        }
        return !beanProperties(element).isEmpty();
    }

    /**
     * A property the runtime serializer writes must be read through a supported member and must not
     * carry an unresolved type variable. Properties without read access are not serialized at all.
     */
    private boolean hasUnsupportedSerializedBeanProperty(ClassElement element) {
        for (PropertyElement property : BeanSerdeShapeResolver.introspectedProperties(element)) {
            if (!isSupportedReadAccess(element, property) || !BeanSerdeShapeResolver.isSerialized(property)) {
                continue;
            }
            ClassElement readType = property.getReadType().orElse(null);
            if (readType == null || hasTypeVariable(readType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * A property the runtime deserializer reads must be written through a supported member and must
     * not carry an unresolved type variable. Properties without write access are unknown on input.
     */
    private boolean hasUnsupportedDeserializedBeanProperty(ClassElement element) {
        for (PropertyElement property : BeanSerdeShapeResolver.introspectedProperties(element)) {
            if (!isSupportedWriteAccess(element, property) || !BeanSerdeShapeResolver.isDeserialized(property)) {
                continue;
            }
            ClassElement writeType = property.getWriteType().orElse(null);
            if (writeType == null || hasTypeVariable(writeType)) {
                return true;
            }
        }
        return false;
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

    private boolean hasDuplicateSerializedNames(ClassElement element) {
        Set<String> serializedNames = new HashSet<>();
        for (PropertyElement property : beanProperties(element)) {
            String serializedName = property.stringValue(SerdeConfig.class, SerdeConfig.PROPERTY).orElse(property.getName());
            if (!serializedNames.add(serializedName)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasDirectIterableProperties(ClassElement element) {
        for (PropertyElement property : beanProperties(element)) {
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
        collectJacksonAnnotationNames(element.getAnnotationNames(), annotations, SUPPORTED_ENUM_JACKSON_ANNOTATIONS);
        for (EnumConstantElement enumConstant : ((EnumElement) element).elements()) {
            collectJacksonAnnotationNames(enumConstant.getAnnotationNames(), annotations, SUPPORTED_ENUM_JACKSON_ANNOTATIONS);
        }
        for (Element field : element.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyDeclared())) {
            collectJacksonAnnotationNames(field.getAnnotationNames(), annotations, SUPPORTED_ENUM_JACKSON_ANNOTATIONS);
        }
        for (MethodElement method : element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared())) {
            collectJacksonAnnotationNames(method.getAnnotationNames(), annotations, SUPPORTED_ENUM_JACKSON_ANNOTATIONS);
        }
        return annotations;
    }

    private boolean hasSubtypedSupertype(ClassElement element) {
        for (ClassElement interfaceElement : element.getInterfaces()) {
            if (interfaceElement.hasDeclaredAnnotation(SerdeConfig.SerSubtyped.class) || hasSubtypedSupertype(interfaceElement)) {
                return true;
            }
        }
        ClassElement superType = element.getSuperType().orElse(null);
        return superType != null
            && (superType.hasDeclaredAnnotation(SerdeConfig.SerSubtyped.class) || hasSubtypedSupertype(superType));
    }

    private boolean hasSubtypedPropertyTypes(ClassElement element) {
        for (PropertyElement property : beanProperties(element)) {
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

    /**
     * An XML root element name is written through the introspection metadata the runtime serializer
     * attaches to the document argument, which the generated serializers do not carry.
     */
    private boolean hasXmlRootElement(ClassElement element) {
        return element.booleanValue(SerdeConfig.class, SerdeConfig.XML_ROOT_ELEMENT).orElse(false)
            || element.hasAnnotation(JAXB_XML_ROOT_ELEMENT)
            || element.hasAnnotation("com.fasterxml.jackson.annotation.JsonRootName");
    }

    private boolean usesDocumentIds(ClassElement element) {
        return hasAnnotationMetadata(element, annotationMetadata -> annotationMetadata.enumValue(SerdeConfig.SerManagedRef.class,
                SerdeConfig.SerManagedRef.SCOPE, SerdeConfig.SerManagedRef.Scope.class).orElse(null) == SerdeConfig.SerManagedRef.Scope.DOCUMENT
            || annotationMetadata.enumValue(SerdeConfig.class, SerdeConfig.ID_REFERENCE, SerdeConfig.IdReference.class).isPresent());
    }

    private boolean hasUnsupportedPropertySerdeConfig(ClassElement element, boolean propertyExclusionSupported, boolean constructorBound) {
        Predicate<AnnotationMetadata> unsupported = annotationMetadata -> hasUnsupportedSerdeConfigMetadata(annotationMetadata, propertyExclusionSupported);
        for (PropertyElement property : beanProperties(element)) {
            if (hasUnsupportedPropertyMetadata(property, unsupported)) {
                return true;
            }
        }
        if ((element.isRecord() || constructorBound) && hasUnsupportedConstructorParameterMetadata(element, unsupported)) {
            return true;
        }
        if (!element.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyInstance().onlyDeclared().annotated(unsupported)).isEmpty()) {
            return true;
        }
        return !element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyInstance().onlyDeclared().annotated(unsupported)).isEmpty();
    }

    private static boolean hasUnsupportedPropertyMetadata(PropertyElement property, Predicate<AnnotationMetadata> unsupported) {
        return unsupported.test(property.getAnnotationMetadata())
            || property.getReadMethod().map(method -> unsupported.test(method.getAnnotationMetadata())).orElse(false)
            || property.getWriteMethod().map(method -> unsupported.test(method.getAnnotationMetadata())).orElse(false);
    }

    private static boolean hasUnsupportedConstructorParameterMetadata(ClassElement element, Predicate<AnnotationMetadata> unsupported) {
        MethodElement primaryConstructor = element.getPrimaryConstructor().orElse(null);
        if (primaryConstructor == null) {
            return false;
        }
        for (ParameterElement parameter : primaryConstructor.getParameters()) {
            if (unsupported.test(parameter.getAnnotationMetadata())) {
                return true;
            }
        }
        return false;
    }

    /**
     * A type-level ignore configuration is honored by the generated bean serdes. The annotation on a
     * member configures the nested value instead, which only the runtime serde supports.
     */
    private boolean hasUnsupportedIgnoredConfig(ClassElement element, boolean propertyExclusionSupported) {
        if (hasDeclaredMemberAnnotation(element, SerdeConfig.SerIgnored.class)) {
            return true;
        }
        return !propertyExclusionSupported && element.hasAnnotation(SerdeConfig.SerIgnored.class);
    }

    private boolean hasUnsupportedIncludedConfig(ClassElement element, boolean propertyExclusionSupported) {
        if (hasDeclaredMemberAnnotation(element, SerdeConfig.SerIncluded.class)) {
            return true;
        }
        return !propertyExclusionSupported && element.hasAnnotation(SerdeConfig.SerIncluded.class);
    }

    /**
     * Whether a member declares the annotation itself. Member metadata also exposes the annotations of
     * the declaring type, so a plain {@code hasAnnotation} would report every type-level annotation.
     */
    private boolean hasDeclaredMemberAnnotation(ClassElement element, Class<? extends Annotation> annotation) {
        if (element.getPrimaryConstructor().map(c -> hasDeclaredAnnotation(c, annotation)).orElse(false)) {
            return true;
        }
        if (beanProperties(element).stream().anyMatch(p -> p.hasDeclaredAnnotation(annotation))) {
            return true;
        }
        if (!element.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyInstance().onlyDeclared()
            .annotated(a -> a.hasDeclaredAnnotation(annotation))).isEmpty()) {
            return true;
        }
        return !element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyInstance().onlyDeclared()
            .annotated(a -> a.hasDeclaredAnnotation(annotation))).isEmpty();
    }

    private boolean hasDeclaredMemberAnnotation(ClassElement element, String annotationName) {
        if (element.getPrimaryConstructor().map(c -> hasDeclaredAnnotation(c, annotationName)).orElse(false)) {
            return true;
        }
        if (beanProperties(element).stream().anyMatch(p -> p.hasDeclaredAnnotation(annotationName))) {
            return true;
        }
        if (!element.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyInstance().onlyDeclared()
            .annotated(a -> a.hasDeclaredAnnotation(annotationName))).isEmpty()) {
            return true;
        }
        return !element.getEnclosedElements(ElementQuery.ALL_METHODS.onlyInstance().onlyDeclared()
            .annotated(a -> a.hasDeclaredAnnotation(annotationName))).isEmpty();
    }

    private boolean hasDeclaredAnnotation(MethodElement methodElement, String annotationName) {
        if (methodElement.hasDeclaredAnnotation(annotationName)) {
            return true;
        }
        for (ParameterElement parameter : methodElement.getParameters()) {
            if (parameter.hasDeclaredAnnotation(annotationName)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasDeclaredAnnotation(MethodElement methodElement, Class<? extends Annotation> annotation) {
        if (methodElement.hasDeclaredAnnotation(annotation)) {
            return true;
        }
        for (ParameterElement parameter : methodElement.getParameters()) {
            if (parameter.hasDeclaredAnnotation(annotation)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSerValueInPropertyTypes(ClassElement element) {
        for (PropertyElement property : beanProperties(element)) {
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
        if (beanProperties(element).stream().anyMatch(p -> p.hasAnnotation(SERDEABLE_SERIALIZABLE))) {
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
        if (beanProperties(element).stream().anyMatch(p -> p.hasAnnotation(SERDEABLE_DESERIALIZABLE))) {
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
        return hasCustomSerdeClass(element.getAnnotationMetadata());
    }

    /**
     * Checks whether a custom {@link Serializer} or {@link Deserializer} is configured, including
     * configuration inherited from a supertype or interface.
     *
     * <p>The configured type is resolved by name because during annotation processing the referenced
     * serializer or deserializer is often part of the same compilation round and therefore cannot be
     * loaded as a {@link Class}.</p>
     *
     * @param annotationMetadata The annotation metadata
     * @return Whether a custom serializer or deserializer is configured
     */
    private boolean hasCustomSerdeClass(AnnotationMetadata annotationMetadata) {
        return hasCustomSerdeClass(annotationMetadata, SerdeConfig.SERIALIZER_CLASS, DEFAULT_SERIALIZER_CLASS)
            || hasCustomSerdeClass(annotationMetadata, SerdeConfig.DESERIALIZER_CLASS, DEFAULT_DESERIALIZER_CLASS);
    }

    private boolean hasCustomSerdeClass(AnnotationMetadata annotationMetadata, String member, String defaultType) {
        return annotationMetadata.stringValue(SerdeConfig.class, member).filter(type -> !type.equals(defaultType)).isPresent()
            || Arrays.stream(annotationMetadata.stringValues(SerdeConfig.class, member)).anyMatch(type -> !type.equals(defaultType));
    }

    private boolean hasPropertyNamedIgnored(ClassElement element) {
        for (PropertyElement property : beanProperties(element)) {
            if ("ignored".equals(property.getName())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasUnsupportedSerdeConfigMetadata(AnnotationMetadata annotationMetadata, boolean propertyExclusionSupported) {
        if (!propertyExclusionSupported
            && (annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.IGNORED).orElse(false)
            || annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.IGNORED_SERIALIZATION).orElse(false)
            || annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.IGNORED_DESERIALIZATION).orElse(false)
            || annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.READ_ONLY).orElse(false)
            || annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.WRITE_ONLY).orElse(false)
            || SerdePropertyAccess.hasRestrictedAccess(annotationMetadata))) {
            return true;
        }
        return annotationMetadata.stringValue(SerdeConfig.class, SerdeConfig.FILTER).isPresent()
            || annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.MERGE).orElse(false)
            || FormatConfiguration.from(annotationMetadata) != null
            || hasFeatureOverrides(annotationMetadata)
            || hasSerializeAsOverride(annotationMetadata)
            || hasDeserializeAsOverride(annotationMetadata)
            || hasCustomNaming(annotationMetadata)
            || hasCustomSerdeClass(annotationMetadata)
            || hasBindableDefaultValue(annotationMetadata);
    }

    /**
     * A declared default value is applied by the runtime deserializer when the property is absent.
     */
    private boolean hasBindableDefaultValue(AnnotationMetadata annotationMetadata) {
        return annotationMetadata.stringValue(BINDABLE, BINDABLE_DEFAULT_VALUE).isPresent();
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

    private boolean hasCustomNaming(ClassElement element) {
        return hasCustomNaming(element.getAnnotationMetadata());
    }

    /**
     * A naming strategy resolved during annotation processing is written into the property names the
     * generated serdes use. Only a strategy that has to be looked up at runtime needs the runtime serde.
     */
    private boolean hasCustomNaming(AnnotationMetadata annotationMetadata) {
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

    /**
     * The state of one analysis: the type, its shape and the first reason each direction fell back.
     */
    private static final class Analysis {

        private final ClassElement element;
        private final ShapeKind shapeKind;
        private final Map<FallbackReason, String> serializerReasons = new LinkedHashMap<>();
        private final Map<FallbackReason, String> deserializerReasons = new LinkedHashMap<>();

        private Analysis(ClassElement element, ShapeKind shapeKind) {
            this.element = element;
            this.shapeKind = shapeKind;
        }

        /**
         * @return {@code true} while at least one direction can still be generated
         */
        private boolean open() {
            return !bothFailed();
        }

        private boolean bothFailed() {
            return !serializerReasons.isEmpty() && !deserializerReasons.isEmpty();
        }

        private boolean serializerOpen() {
            return serializerReasons.isEmpty();
        }

        private boolean deserializerOpen() {
            return deserializerReasons.isEmpty();
        }

        /**
         * Generated bean serdes honor ignored, read-only and write-only properties; the record
         * generators still hand those shapes to the runtime serde.
         */
        private boolean propertyExclusionSupported() {
            return shapeKind == ShapeKind.DEFAULT_CONSTRUCTOR_BEAN;
        }

        /**
         * Records the reason for both directions; the first reason recorded for a direction is kept.
         *
         * @return {@code true} once both directions fell back, so the analysis is complete
         */
        private boolean failBoth(FallbackReason reason) {
            return failBoth(reason, reason.message());
        }

        private boolean failBoth(FallbackReason reason, String message) {
            recordReason(serializerReasons, reason, message);
            recordReason(deserializerReasons, reason, message);
            return bothFailed();
        }

        private boolean failSerializer(FallbackReason reason) {
            recordReason(serializerReasons, reason, reason.message());
            return bothFailed();
        }

        private boolean failDeserializer(FallbackReason reason) {
            recordReason(deserializerReasons, reason, reason.message());
            return bothFailed();
        }

        private static void recordReason(Map<FallbackReason, String> reasons, FallbackReason reason, String message) {
            if (reasons.isEmpty()) {
                reasons.put(reason, message);
            }
        }

        private SimpleSerdeShapeDecision decision() {
            return new SimpleSerdeShapeDecision(
                shapeKind,
                serializerReasons.isEmpty(),
                deserializerReasons.isEmpty(),
                serializerReasons,
                deserializerReasons
            );
        }
    }
}
