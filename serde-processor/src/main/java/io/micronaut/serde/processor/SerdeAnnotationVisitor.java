/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.serde.processor;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Creator;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.InstantiationUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementModifier;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.beans.visitor.IntrospectedTypeElementVisitor;
import io.micronaut.inject.processing.ProcessingException;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.annotation.SerdeImport;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.annotation.SerdeableGenerated;
import io.micronaut.serde.FormatConfiguration;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;
import io.micronaut.serde.processor.sourcegen.SerdeSourceGenClassNaming;
import io.micronaut.serde.processor.sourcegen.SimpleSerdeShapeAnalyzer;
import io.micronaut.serde.processor.sourcegen.SimpleSerdeShapeDecision;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A visitor that provides validation and extended handling for JSON annotations.
 */
public class SerdeAnnotationVisitor implements TypeElementVisitor<SerdeConfig, SerdeConfig> {

    private static final String DEFAULT_REF_ALIAS_NAME = "defaultReference";
    private static final String JSONB_NILLABLE = "jakarta.json.bind.annotation.JsonbNillable";
    private static final String JSON_AUTO_DETECT = "com.fasterxml.jackson.annotation.JsonAutoDetect";
    private static final String JSON_AUTO_DETECT_ANY = "ANY";
    private static final String VISIBILITY = "visibility";
    private static final String JAXB_ANNOTATION_PREFIX = "jakarta.xml.bind.annotation.";
    private static final String JAXB_XML_ROOT_ELEMENT = JAXB_ANNOTATION_PREFIX + "XmlRootElement";
    private static final String JAXB_XML_TYPE = JAXB_ANNOTATION_PREFIX + "XmlType";
    private static final String JAXB_XML_ENUM = JAXB_ANNOTATION_PREFIX + "XmlEnum";
    private static final String JAXB_XML_ACCESSOR_ORDER = JAXB_ANNOTATION_PREFIX + "XmlAccessorOrder";
    private static final String JAXB_XML_ACCESSOR_TYPE = JAXB_ANNOTATION_PREFIX + "XmlAccessorType";
    private static final String JAXB_XML_TRANSIENT = JAXB_ANNOTATION_PREFIX + "XmlTransient";
    private static final String JAXB_XML_ELEMENT_WRAPPER = JAXB_ANNOTATION_PREFIX + "XmlElementWrapper";
    private static final String JAXB_XML_ELEMENT = JAXB_ANNOTATION_PREFIX + "XmlElement";
    private static final String JAXB_XML_ELEMENT_REF = JAXB_ANNOTATION_PREFIX + "XmlElementRef";
    private static final String JAXB_XML_ID = JAXB_ANNOTATION_PREFIX + "XmlID";
    private static final String JAXB_XML_ID_REF = JAXB_ANNOTATION_PREFIX + "XmlIDREF";
    private static final String JAXB_XML_VALUE = JAXB_ANNOTATION_PREFIX + "XmlValue";
    private static final String JAXB_ELEMENT = "jakarta.xml.bind.JAXBElement";
    private static final String JACKSON_IDENTITY_INFO = "com.fasterxml.jackson.annotation.JsonIdentityInfo";
    private static final String JACKSON_IDENTITY_REFERENCE = "com.fasterxml.jackson.annotation.JsonIdentityReference";
    private static final String JACKSON_PROPERTY_GENERATOR = "com.fasterxml.jackson.annotation.ObjectIdGenerators$PropertyGenerator";
    private static final String JACKSON_SIMPLE_OBJECT_ID_RESOLVER = "com.fasterxml.jackson.annotation.SimpleObjectIdResolver";
    private static final String ID_REFERENCE_SERIALIZER = "io.micronaut.serde.support.serializers.IdReferenceSerializer";
    private static final String ID_REFERENCE_DESERIALIZER = "io.micronaut.serde.support.deserializers.IdReferenceDeserializer";

    private boolean failOnError = true;
    private @Nullable ClassElement currentClass;
    private @Nullable MethodElement anyGetterMethod;
    private @Nullable MethodElement anySetterMethod;
    private @Nullable FieldElement anyGetterField;
    private @Nullable FieldElement anySetterField;
    private @Nullable MethodElement jsonValueMethod;
    private @Nullable FieldElement jsonValueField;
    private @Nullable MethodElement jsonKeyMethod;
    private @Nullable FieldElement jsonKeyField;
    private final Set<MethodElement> readMethods = new HashSet<>(20);
    private final Set<MethodElement> writeMethods = new HashSet<>(20);
    private final Set<String> elementVisitedAsSubtype = new HashSet<>(10);
    private final SimpleSerdeShapeAnalyzer sourceGenShapeAnalyzer = new SimpleSerdeShapeAnalyzer();
    private SerdeConfig.SerCreatorMode creatorMode = SerdeConfig.SerCreatorMode.PROPERTIES;

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return CollectionUtils.setOf(
                "com.fasterxml.jackson.annotation.*",
                "jakarta.json.bind.annotation.*",
                "io.micronaut.serde.annotation.*",
                "org.bson.codecs.pojo.annotations.*",
                "io.micronaut.serde.config.annotation.*",
                "tools.jackson.databind.annotation.*",
                "tools.jackson.dataformat.annotation.*",
                "tools.jackson.dataformat.xml.annotation.*",
                "jakarta.xml.bind.annotation.*"
        );
    }

    @Override
    public void visitField(FieldElement element, VisitorContext context) {
        sanitizeCoreJsonPropertyFieldAnnotation(element);
        checkForErrors(element, context);
        checkForFieldErrors(element);
    }

    private void checkForFieldErrors(FieldElement element) {
        if (!failOnError) {
            return;
        }
        if (element.hasDeclaredAnnotation(SerdeConfig.SerAnyGetter.class)) {
            if (element.hasDeclaredAnnotation(SerdeConfig.SerUnwrapped.class)) {
                throw new ProcessingException(element, "A field annotated with AnyGetter cannot be unwrapped");
            } else if (element.hasDeclaredAnnotation(SerdeConfig.SerValue.class)) {
                throw new ProcessingException(element, "A field annotated with AnyGetter cannot be a JsonValue");
            } else if (!element.getGenericField().isAssignable(Map.class)) {
                throw new ProcessingException(element, "A field annotated with AnyGetter must be a Map");
            } else {
                if (anyGetterField != null) {
                    throw new ProcessingException(element, "Only a single AnyGetter field is supported, another defined: " + anyGetterField.getDescription(true));
                } else if (anyGetterMethod != null) {
                    throw new ProcessingException(element, "Cannot define both an AnyGetter field and an AnyGetter method: " + anyGetterMethod.getDescription(true));
                } else {
                    this.anyGetterField = element;
                }
            }
        } else if (element.hasDeclaredAnnotation(SerdeConfig.SerAnySetter.class)) {
            if (creatorMode == SerdeConfig.SerCreatorMode.DELEGATING) {
                throw new ProcessingException(element, "A field annotated with AnySetter cannot use DELEGATING creation");
            } else if (element.hasDeclaredAnnotation(SerdeConfig.SerUnwrapped.class)) {
                throw new ProcessingException(element, "A field annotated with AnySetter cannot be unwrapped");
            } else if (!element.getGenericField().isAssignable(Map.class)) {
                throw new ProcessingException(element, "A field annotated with AnySetter must be a Map");
            } else {
                if (anySetterField != null) {
                    throw new ProcessingException(element, "Only a single AnySetter field is supported, another defined: " + anySetterField.getDescription(true));
                } else if (anySetterMethod != null) {
                    throw new ProcessingException(element, "Cannot define both an AnySetter field and an AnySetter method: " + anySetterMethod.getDescription(true));
                } else {
                    this.anySetterField = element;
                }
            }
        } else if (element.hasDeclaredAnnotation(SerdeConfig.SerValue.class)) {
            if (jsonValueField != null) {
                throw new ProcessingException(element, "A JsonValue field is already defined: " + jsonValueField);
            } else if (jsonValueMethod != null) {
                throw new ProcessingException(element, "A JsonValue method is already defined: " + jsonValueMethod);
            } else {
                this.jsonValueField = element;
            }
        }
        if (element.hasDeclaredAnnotation(SerdeConfig.SerKey.class)) {
            if (jsonKeyField != null) {
                throw new ProcessingException(element, "A JsonKey field is already defined: " + jsonKeyField);
            } else if (jsonKeyMethod != null) {
                throw new ProcessingException(element, "A JsonKey method is already defined: " + jsonKeyMethod);
            } else {
                this.jsonKeyField = element;
            }
        }
    }

    @Override
    public void visitConstructor(ConstructorElement element, VisitorContext context) {
        checkForErrors(element, context);
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        checkForErrors(element, context);
        AnnotationMetadata methodMetadata = element.getMethodAnnotationMetadata();
        if (methodMetadata.hasDeclaredAnnotation(SerdeConfig.META_ANNOTATION_PROPERTY) ||
            methodMetadata.stringValue(SerdeConfig.class, SerdeConfig.PROPERTY).isPresent()) {
            ParameterElement[] parameters = element.getParameters();
            if (element.isStatic()) {
                throw new ProcessingException(element, "A method annotated with JsonProperty cannot be static");
            } else if (parameters.length == 0) {
                if (element.getReturnType().getName().equals("void")) {
                    throw new ProcessingException(element, "A method annotated with JsonProperty cannot return void");
                } else if (!readMethods.contains(element)) {
                    element.annotate(Executable.class);
                    element.annotate(SerdeConfig.SerGetter.class);
                }
            } else if (parameters.length == 1) {
                if (!writeMethods.contains(element)) {
                    element.annotate(Executable.class);
                    element.annotate(SerdeConfig.SerSetter.class);
                }
            } else {
                throw new ProcessingException(element, "A method annotated with JsonProperty must specify at most 1 argument");
            }
        } else if (methodMetadata.hasDeclaredAnnotation(SerdeConfig.SerGetter.class)) {
            if (element.isStatic()) {
                throw new ProcessingException(element, "A method annotated with JsonGetter cannot be static");
            } else if (element.getReturnType().getName().equals("void")) {
                throw new ProcessingException(element, "A method annotated with JsonGetter cannot return void");
            } else if (element.hasParameters()) {
                throw new ProcessingException(element, "A method annotated with JsonGetter cannot define arguments");
            }
        } else if (methodMetadata.hasDeclaredAnnotation(SerdeConfig.SerSetter.class)) {
            if (element.isStatic()) {
                throw new ProcessingException(element, "A method annotated with JsonSetter cannot be static");
            } else {
                final ParameterElement[] parameters = element.getParameters();
                if (parameters.length != 1) {
                    throw new ProcessingException(element, "A method annotated with JsonSetter must specify exactly 1 argument");
                }
            }
        } else if (methodMetadata.hasDeclaredAnnotation(SerdeConfig.SerAnyGetter.class)) {
            if (this.anyGetterMethod == null) {
                this.anyGetterMethod = element;
            } else {
                throw new ProcessingException(element, "Type already defines a method annotated with JsonAnyGetter: " + anyGetterMethod.getDescription(true));
            }

            if (methodMetadata.hasDeclaredAnnotation(SerdeConfig.SerUnwrapped.class)) {
                throw new ProcessingException(element, "A method annotated with AnyGetter cannot be unwrapped");
            } else if (element.isStatic()) {
                throw new ProcessingException(element, "A method annotated with AnyGetter cannot be static");
            } else if (!element.getGenericReturnType().isAssignable(Map.class)) {
                throw new ProcessingException(element, "A method annotated with AnyGetter must return a Map");
            } else if (element.hasParameters()) {
                throw new ProcessingException(element, "A method annotated with AnyGetter cannot define arguments");
            }
        } else if (methodMetadata.hasDeclaredAnnotation(SerdeConfig.SerAnySetter.class)) {
            if (this.anySetterMethod == null) {
                this.anySetterMethod = element;
            } else {
                throw new ProcessingException(element, "Type already defines a method annotated with JsonAnySetter: " + anySetterMethod.getDescription(true));
            }
            if (methodMetadata.hasDeclaredAnnotation(SerdeConfig.SerUnwrapped.class)) {
                throw new ProcessingException(element, "A method annotated with AnyGetter cannot be unwrapped");
            } else if (element.isStatic()) {
                throw new ProcessingException(element, "A method annotated with AnySetter cannot be static");
            } else {
                final ParameterElement[] parameters = element.getParameters();
                if (parameters.length == 1) {
                   if (!parameters[0].getGenericType().isAssignable(Map.class)) {
                       throw new ProcessingException(element, "A method annotated with AnySetter must either define a single parameter of type Map or define exactly 2 parameters, the first of which should be of type String");
                   }
                } else if (parameters.length != 2 || !parameters[0].getGenericType().isAssignable(String.class)) {
                    throw new ProcessingException(element, "A method annotated with AnySetter must either define a single parameter of type Map or define exactly 2 parameters, the first of which should be of type String");
                }
            }
        } else if (methodMetadata.hasDeclaredAnnotation(SerdeConfig.SerValue.class)) {
            if (jsonValueField != null) {
                throw new ProcessingException(element, "A JsonValue field is already defined: " + jsonValueField);
            } else if (jsonValueMethod != null) {
                throw new ProcessingException(element, "A JsonValue method is already defined: " + jsonValueMethod);
            } else {
                this.jsonValueMethod = element;
            }
        }
        if (methodMetadata.hasDeclaredAnnotation(SerdeConfig.SerKey.class)) {
            if (element.isStatic()) {
                throw new ProcessingException(element, "A JsonKey method cannot be static");
            } else if (element.getReturnType().getName().equals("void")) {
                throw new ProcessingException(element, "A JsonKey method cannot return void");
            } else if (element.hasParameters()) {
                throw new ProcessingException(element, "A JsonKey method cannot define arguments");
            } else if (jsonKeyField != null) {
                throw new ProcessingException(element, "A JsonKey field is already defined: " + jsonKeyField);
            } else if (jsonKeyMethod != null) {
                throw new ProcessingException(element, "A JsonKey method is already defined: " + jsonKeyMethod);
            } else {
                this.jsonKeyMethod = element;
            }
        }
    }

    private void checkForErrors(Element element, VisitorContext context) {
        if (!failOnError) {
            return;
        }
        if (element instanceof MethodElement methodElement) {
            if (readMethods.contains(methodElement) && !methodElement.hasParameters()) {
                // handled by PropertyElement
                return;
            } else if (writeMethods.contains(methodElement) && methodElement.getParameters().length == 1) {
                // handled by PropertyElement
                return;
            }
        }

        if (element instanceof MethodElement && element.hasDeclaredAnnotation(SerdeConfig.class) && element.isPrivate()) {
            throw new ProcessingException(element, "JSON annotations cannot be used on private methods and constructors");
        }
        checkJsonAutoDetect(element);
        validateJaxbAnnotations(element, context);
        if (!(element instanceof ClassElement) && element.hasDeclaredAnnotation(JACKSON_IDENTITY_INFO)) {
            throw new ProcessingException(element, "Annotation @JsonIdentityInfo is only supported on types");
        }
        final String error = element.stringValue(SerdeConfig.SerError.class).orElse(null);
        if (error != null) {
            throw new ProcessingException(element, error);
        }
        ClassElement propertyType = resolvePropertyType(element);
        if (propertyType == null) {
            return;
        }
        final boolean isBasicType = isBasicType(propertyType);
        if (isBasicType) {
            String defaultValue = element.stringValue(Bindable.class, "defaultValue").orElse(null);
            if (defaultValue != null) {
                Class t;
                if (propertyType.isPrimitive()) {
                    t = ClassUtils.getPrimitiveType(propertyType.getName())
                            .map(ReflectionUtils::getWrapperType)
                            .orElse(null);
                } else {
                    t =  ClassUtils.forName(propertyType.getName(), getClass().getClassLoader()).orElse(null);
                }
                if (t != null) {
                    try {
                        if (ConversionService.SHARED.canConvert(String.class, t)) {
                            ConversionService.SHARED.convertRequired(defaultValue, t);
                        }
                    } catch (ConversionErrorException e) {
                        throw new ProcessingException(element, "Invalid defaultValue [" + defaultValue + "] specified: " + e.getConversionError().getCause().getMessage());
                    }
                }
            }
        }
        final String pattern = element.stringValue(SerdeConfig.class, SerdeConfig.PATTERN).orElse(null);
        if (pattern != null && failOnError) {
            if (isNumberType(propertyType)) {
                try {
                    new DecimalFormat(pattern);
                } catch (Exception e) {
                    throw new ProcessingException(element, "Specified pattern [" + pattern + "] is not a valid decimal format. See the javadoc for DecimalFormat: " + e.getMessage());
                }
            } else if (propertyType.isAssignable(Temporal.class)) {
                try {
                    var ignored = DateTimeFormatter.ofPattern(pattern);
                } catch (Exception e) {
                    throw new ProcessingException(element, "Specified pattern [" + pattern + "] is not a valid date format. See the javadoc for DateTimeFormatter: " + e.getMessage());
                }
            }
        }

        handleReferences(element, context, propertyType, isBasicType);

        if (hasAnnotationOnElement(element, SerdeConfig.SerUnwrapped.class)) {
            if (isBasicType(propertyType)) {
                throw new ProcessingException(element, "Unwrapped cannot be declared on basic types");
            }
            final List<String> thatProperties = resolvePropertyNames(context, propertyType, element);
            final List<String> thisProperties = resolvePropertyNames(context, currentClass(), null);
            String currentUnwrappedName = null;
            if (element instanceof TypedElement te) {
                currentUnwrappedName = resolvePropertyName(te);
            }
            for (String thisProperty : thisProperties) {
                if (currentUnwrappedName != null && thisProperty.equals(currentUnwrappedName)) {
                    // Allow inner properties to have the same name as the outer unwrapped property's own name
                    // because the outer property itself is not materialized in JSON when unwrapped.
                    continue;
                }
                for (String thatProperty : thatProperties) {
                    if (thisProperty.equals(thatProperty)) {
                        throw new ProcessingException(element, "Unwrapped property contains a property [" + thatProperty + "] that conflicts with an existing property of the outer type: " + currentClass().getName() + ". Consider specifying a prefix or suffix to disambiguate this conflict.");
                    }
                }
            }
        }
    }

    private void validateJaxbAnnotations(Element element, VisitorContext context) {
        if (element.hasDeclaredAnnotation("jakarta.xml.bind.annotation.XmlElementRef")
            && element.getAnnotationMetadata().classValue("jakarta.xml.bind.annotation.XmlElementRef", "type")
                .map(type -> JAXB_ELEMENT.equals(type.getName())).orElse(false)) {
            context.warn("JAXBElement references are not supported", element);
        }
        if (element.hasDeclaredAnnotation(JAXB_XML_ELEMENT_WRAPPER)) {
            ClassElement type = resolvePropertyType(element);
            if (type != null && !type.isArray() && !type.isAssignable(Collection.class)) {
                throw new ProcessingException(element, "XmlElementWrapper can only be used on collection or array properties");
            }
            if (element instanceof TypedElement typedElement
                && element.stringValue(JAXB_XML_ELEMENT_WRAPPER, "name")
                    .filter(name -> "##default".equals(name))
                    .isPresent()) {
                element.annotate(SerdeConfig.class, builder -> builder.member(SerdeConfig.WRAPPER_PROPERTY, resolvePropertyName(typedElement)));
            }
        }
        AnnotationValue<Annotation> xmlElement = element.getAnnotationMetadata().getAnnotation(JAXB_XML_ELEMENT);
        if (xmlElement != null) {
            xmlElement.annotationClassValue("type")
                .filter(type -> !"jakarta.xml.bind.annotation.XmlElement$DEFAULT".equals(type.getName()))
                .flatMap(type -> context.getClassElement(type.getName()))
                .ifPresent(type -> validateJaxbElementType(element, type));
        }
    }

    private void validateJaxbElementType(Element element, ClassElement typeOverride) {
        ClassElement propertyType = resolvePropertyType(element);
        if (propertyType == null) {
            return;
        }
        ClassElement valueType = resolveRefType(propertyType);
        if (!typeOverride.isAssignable(valueType)) {
            throw new ProcessingException(element, "XmlElement type [" + typeOverride.getName()
                + "] must be assignable to property type [" + valueType.getName() + "]");
        }
    }

    private void handleReferences(Element element, VisitorContext context, ClassElement propertyType, boolean isBasicType) {
        if (element.enumValue(SerdeConfig.SerManagedRef.class, SerdeConfig.SerManagedRef.SCOPE,
                SerdeConfig.SerManagedRef.Scope.class).orElse(null) == SerdeConfig.SerManagedRef.Scope.DOCUMENT) {
            return;
        }
        handleReferenceProperty(
            element,
            context,
            propertyType,
            isBasicType,
            SerdeConfig.SerManagedRef.class,
            SerdeConfig.SerBackRef.class,
            SerdeConfig.SerManagedRef.ALIAS);
        handleReferenceProperty(
            element,
            context,
            propertyType,
            isBasicType,
            SerdeConfig.SerBackRef.class,
            SerdeConfig.SerManagedRef.class,
            SerdeConfig.SerBackRef.ALIAS);
    }

    private void handleReferenceProperty(Element element,
                                           VisitorContext context,
                                           ClassElement propertyType,
                                           boolean isBasicType,
                                           Class<? extends Annotation> refClass,
                                           Class<? extends Annotation> inverseRefClass,
                                           String aliasProperty) {
        if (hasAnnotationOnElement(element, refClass)) {
            if (element.stringValue(refClass).isPresent()) {
                // Already managed
                return;
            }
            if (hasAnnotationOnElement(element, SerdeConfig.SerUnwrapped.class)) {
                throw new ProcessingException(element, "Managed references cannot be unwrapped");
            }
            if (isBasicType) {
                throw new ProcessingException(element, "Managed references cannot be declared on basic types");
            }

            final String refName = element.stringValue(refClass, aliasProperty).orElse(DEFAULT_REF_ALIAS_NAME);
            final List<TypedElement> inverseElements = resolveInverseElements(
                context,
                resolveRefType(propertyType),
                inverseRefClass,
                aliasProperty,
                refName);

            final int i = inverseElements.size();
            if (i == 0) {
                throw new ProcessingException(element, "No inverse property found for reference of type " + propertyType.getName() + " and reference: " + refName);
            } else if (i > 1) {
                throw new ProcessingException(element, "More than one potential inverse property found " + inverseElements + ", consider specifying a value to the reference to configure the association");
            } else {
                final TypedElement otherElement = inverseElements.iterator().next();
                if (!isCompatibleInverseSide(otherElement.getGenericType(), currentClass())) {
                    throw new ProcessingException(element, "Managed reference declares an incompatible inverse property [" + otherElement +
                        "]. The inverse side should be a map, collection, bean or array of the same type as the property.");
                } else {
                    element.annotate(refClass, (builder) ->
                        builder.value(otherElement.getName())
                    );
                }
            }
        }
    }

    private boolean hasAnnotationOnElement(Element element, Class<? extends Annotation> managedRefClass) {
        return element.hasDeclaredAnnotation(managedRefClass) || (
                element instanceof PropertyElement && element.hasAnnotation(managedRefClass));
    }

    private String resolvePropertyName(TypedElement thisProperty) {
        return thisProperty.stringValue(SerdeConfig.class, SerdeConfig.PROPERTY)
                                      .orElseGet(() -> {
                                          if (thisProperty instanceof MethodElement) {
                                              return NameUtils.getPropertyNameForGetter(thisProperty.getName());
                                          }
                                          return thisProperty.getName();
                                      });
    }

    private ClassElement resolveRefType(ClassElement propertyType) {
        if (propertyType.isArray()) {
            return propertyType.fromArray();
        } else if (propertyType.isAssignable(Iterable.class)) {
            return propertyType.getFirstTypeArgument().orElse(propertyType);
        } else if (propertyType.isAssignable(Map.class)) {
            final List<? extends ClassElement> boundGenericTypes = propertyType.getBoundGenericTypes();
            if (boundGenericTypes.size() == 2) {
                return boundGenericTypes.get(1);
            } else {
                return propertyType;
            }
        }
        return propertyType;
    }

    private List<TypedElement> resolveInverseElements(VisitorContext context,
                                                      ClassElement propertyType,
                                                      Class<? extends Annotation> refType,
                                                      String aliasPropertyName,
                                                      String ref) {
        Set<Introspected.AccessKind> accessKindSet = resolveAccessSet(context, propertyType);
        final List<TypedElement> otherElements = new ArrayList<>();
        if (accessKindSet.contains(Introspected.AccessKind.METHOD)) {
            propertyType.getBeanProperties()
                    .stream()
                    .filter(p ->
                       isMappedCandidate(refType, aliasPropertyName, ref, p) &&
                        p.hasAnnotation(refType) &&
                        isCompatibleInverseSide(p.getGenericType(), currentClass())
                    ).forEach(otherElements::add);
        }
        if (accessKindSet.contains(Introspected.AccessKind.FIELD)) {
            final List<FieldElement> fields = propertyType
                    .getEnclosedElements(ElementQuery.ALL_FIELDS
                                                 .onlyInstance()
                                                 .annotated(ann -> isMappedCandidate(refType, aliasPropertyName, ref, ann)
                                                     && ann.hasDeclaredAnnotation(refType))
                                                 .modifiers(m -> m.contains(ElementModifier.PUBLIC))
                                                 .typed(t -> isCompatibleInverseSide(t.getGenericType(),
                                                                                     currentClass())));
            otherElements.addAll(fields);

        }
        return otherElements;
    }

    private List<String> resolvePropertyNames(VisitorContext context,
                                              ClassElement propertyType,
                                              @Nullable Element annotationSource) {
        Set<String> includeSet;
        String[] includedSource = annotationSource == null ? null : annotationSource.stringValues(SerdeConfig.SerIncluded.class);
        String[] includedType = propertyType.stringValues(SerdeConfig.SerIncluded.class);
        if (ArrayUtils.isEmpty(includedSource)) {
            if (ArrayUtils.isEmpty(includedType)) {
                includeSet = null;
            } else {
                includeSet = CollectionUtils.setOf(includedType);
            }
        } else {
            includeSet = CollectionUtils.setOf(includedSource);
        }

        Set<String> ignoreSet;
        String[] ignoredSource = annotationSource == null ? null : annotationSource.stringValues(SerdeConfig.SerIgnored.class);
        String[] ignoredType = propertyType.stringValues(SerdeConfig.SerIgnored.class);
        if (ArrayUtils.isEmpty(ignoredSource)) {
            if (ArrayUtils.isEmpty(ignoredType)) {
                ignoreSet = null;
            } else {
                ignoreSet = CollectionUtils.setOf(ignoredType);
            }
        } else {
            ignoreSet = CollectionUtils.setOf(ignoredSource);
        }

        Stream<? extends TypedElement> typeElements;
        Set<Introspected.AccessKind> accessKindSet = resolveAccessSet(context, propertyType);
        if (accessKindSet.contains(Introspected.AccessKind.METHOD)) {
            typeElements = propertyType.getBeanProperties().stream()
                .filter(p -> !p.hasDeclaredAnnotation(SerdeConfig.SerIgnored.class));
        } else if (accessKindSet.contains(Introspected.AccessKind.FIELD)) {
            final List<FieldElement> fields = propertyType
                    .getEnclosedElements(ElementQuery.ALL_FIELDS
                                                 .onlyInstance()
                                                 .annotated(ann -> !ann.hasDeclaredAnnotation(SerdeConfig.SerIgnored.class))
                                                 .modifiers(m -> m.contains(ElementModifier.PUBLIC)));
            typeElements = fields.stream();
        } else {
            typeElements = Stream.empty();
        }

        return typeElements
            .map(this::resolvePropertyName)
            .filter(s -> (ignoreSet == null || !ignoreSet.contains(s)) && (includeSet == null || includeSet.contains(s)))
            .toList();
    }

    private boolean isMappedCandidate(Class<? extends Annotation> refType,
                                      String aliasPropertyName,
                                      String ref,
                                      AnnotationMetadata p) {
        return p.stringValue(refType, aliasPropertyName).orElse(DEFAULT_REF_ALIAS_NAME).equals(ref);
    }

    private Set<Introspected.AccessKind> resolveAccessSet(VisitorContext context, ClassElement propertyType) {
        final Introspected.AccessKind[] accessKinds = context.getClassElement(propertyType.getName())
                .map(t -> t.enumValues(Introspected.class,
                                                  "accessKind",
                                                  Introspected.AccessKind.class)).orElse(null);
        return ArrayUtils.isNotEmpty(accessKinds) ? CollectionUtils.setOf(accessKinds) : Collections.singleton(Introspected.AccessKind.METHOD);
    }

    private boolean isCompatibleInverseSide(ClassElement genericType, ClassElement propertyType) {
        if (genericType.isAssignable(propertyType)) {
            return true;
        } else if (genericType.isArray() && genericType.fromArray().isAssignable(propertyType)) {
            return true;
        } else if (genericType.isAssignable(Iterable.class) && genericType.getFirstTypeArgument().map(t -> t.isAssignable(propertyType)).orElse(false)) {
            return true;
        } else if (genericType.isAssignable(Map.class)) {
            final List<? extends ClassElement> types = genericType.getBoundGenericTypes();
            return types.size() == 2 && types.get(1).isAssignable(propertyType);
        }
        return false;
    }

    private boolean isNumberType(ClassElement type) {
        if (type == null) {
            return false;
        }
        return type.isAssignable(Number.class) ||
                (type.isPrimitive() && ClassUtils.getPrimitiveType(type.getName())
                        .map(ReflectionUtils::getWrapperType)
                        .map(Number.class::isAssignableFrom).orElse(false));
    }

    private @Nullable ClassElement resolvePropertyType(Element element) {
        ClassElement type = null;
        if (element instanceof FieldElement fieldElement) {
            type = fieldElement.getGenericField().getType();
        } else if (element instanceof MethodElement methodElement) {
            if (!methodElement.hasParameters()) {
                type = methodElement.getGenericReturnType();
            } else {
                type = methodElement.getParameters()[0].getGenericType();
            }
        } else if (element instanceof PropertyElement propertyElement) {
            return propertyElement.getGenericType();
        }
        return type;
    }

    private void checkJsonAutoDetect(Element element) {
        if (!element.hasDeclaredAnnotation(JSON_AUTO_DETECT)) {
            return;
        }
        for (String member : List.of("fieldVisibility", "getterVisibility", "isGetterVisibility", "setterVisibility", "creatorVisibility")) {
            String visibility = element.stringValue(JSON_AUTO_DETECT, member).orElse(null);
            if (JSON_AUTO_DETECT_ANY.equals(enumName(visibility))) {
                throw new ProcessingException(element, "JsonAutoDetect.Visibility.ANY is not supported");
            }
        }
    }

    private static @Nullable String enumName(@Nullable String value) {
        if (value == null) {
            return null;
        }
        int lastDot = value.lastIndexOf('.');
        if (lastDot > -1) {
            return value.substring(lastDot + 1);
        }
        return value;
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        // reset
        resetForNewClass(element);
        checkForErrors(element, context);
        visitClassInternal(element, context, false);
    }

    private void visitClassSubtypes(ClassElement supertype, VisitorContext context) {
        List<AnnotationValue<SerdeConfig.SerSubtyped.SerSubtype>> subtypes =
            supertype.getDeclaredAnnotationValuesByType(SerdeConfig.SerSubtyped.SerSubtype.class);

        for (AnnotationValue<SerdeConfig.SerSubtyped.SerSubtype> subtypeAnn : subtypes) {
            subtypeAnn.stringValue()
                .flatMap(context::getClassElement)
                .ifPresent(subtype -> {
                    if (!subtype.hasStereotype(SerdeConfig.class)) {
                        subtype.annotate(Serdeable.class);
                        visitSubtype(supertype, subtype, context);
                    }
                });
        }
    }

    @SuppressWarnings("MissingSwitchDefault")
    private void visitSubtype(ClassElement supertype, ClassElement subtype, VisitorContext context) {
        if (elementVisitedAsSubtype.contains(subtype.getName())) {
            return;
        }
        elementVisitedAsSubtype.add(subtype.getName());

        if (failOnError && creatorMode == SerdeConfig.SerCreatorMode.DELEGATING) {
            throw new ProcessingException(subtype, "Inheritance cannot be combined with DELEGATING creation");
        }

        if (!subtype.hasAnnotation(SerdeConfig.SerIgnored.class)) {
            // Replicate the Jackson behaviour of using the ignore annotation from supertype but allow to override it
            AnnotationValue<SerdeConfig.SerIgnored> serIgnored = supertype.getAnnotation(SerdeConfig.SerIgnored.class);
            if (serIgnored != null) {
                subtype.annotate(serIgnored);
            }
            // Re-evaluate ignore properties
            visitProperties(subtype, context);
        }

        final SerdeConfig.SerSubtyped.DiscriminatorValueKind discriminatorValueKind =
            getDiscriminatorValueKind(supertype);
        if (discriminatorValueKind == SerdeConfig.SerSubtyped.DiscriminatorValueKind.DEDUCTION) {
            return;
        }

        Optional<SerdeConfig.SerSubtyped.DiscriminatorType> optionalDiscriminatorType = getDiscriminatorType(supertype);
        if (optionalDiscriminatorType.isPresent() && optionalDiscriminatorType.get() == SerdeConfig.SerSubtyped.DiscriminatorType.EXTERNAL_PROPERTY) {
            throw new ProcessingException(subtype, "EXTERNAL_PROPERTY can only be used for properties. " +
                "Trying to use it for classes will result in inclusion strategy of basic PROPERTY instead.");
        }

        List<String> allNames = new ArrayList<>();

        switch (discriminatorValueKind) {
            case NAME -> {
                Optional<String> typeNameOptional = subtype.stringValue(SerdeConfig.class, SerdeConfig.TYPE_NAME);
                if (typeNameOptional.isPresent()) {
                    allNames.add(typeNameOptional.get());
                } else {
                    for (AnnotationValue<SerdeConfig.SerSubtyped.SerSubtype> parentSubtype : supertype.getDeclaredAnnotationValuesByType(SerdeConfig.SerSubtyped.SerSubtype.class)) {
                        Optional<AnnotationClassValue<?>> annotationClassValue = parentSubtype.annotationClassValue(AnnotationMetadata.VALUE_MEMBER);
                        if (annotationClassValue.isPresent()) {
                            AnnotationClassValue<?> typeNameVal = annotationClassValue.get();
                            String typeName = typeNameVal.getName();
                            if (typeName.equals(subtype.getName())) {
                                parentSubtype.stringValue("name").ifPresent(allNames::add);
                                allNames.addAll(Arrays.asList(parentSubtype.stringValues("names")));
                                break;
                            }
                        }
                    }
                }
                if (allNames.isEmpty()) {
                    // Fallback to class name
                    allNames.add(subtype.getSimpleName());
                }
            }
            case CLASS_NAME -> allNames.add(subtype.getName());
            case CLASS_SIMPLE_NAME -> allNames.add(subtype.getSimpleName());
            case MINIMAL_CLASS -> {
                String superPackage = supertype.getPackage().getName();
                String name = subtype.getName();
                String typeName;
                if (name.startsWith(superPackage)) {
                    typeName = name.substring(superPackage.length());
                } else {
                    typeName = name;
                }
                allNames.add(typeName);
            }
        }

        List<TypePropertyDescriptor> typePropertyDescriptors = new ArrayList<>();
        if (optionalDiscriminatorType.isPresent()
            && optionalDiscriminatorType.get() == SerdeConfig.SerSubtyped.DiscriminatorType.PROPERTY) {
            String directTypeProperty = resolveTypeProperty(supertype)
                .orElseThrow(() -> new ProcessingException(subtype, "Cannot resolve type property for supertype: " + supertype));
            typePropertyDescriptors = resolveTypePropertyDescriptors(subtype, supertype, directTypeProperty, allNames.get(0));
        }
        List<TypePropertyDescriptor> finalTypePropertyDescriptors = typePropertyDescriptors;
        List<String> jsonbTypeInfoPropertyOrder = finalTypePropertyDescriptors.size() > 1 && isJsonbTypeInfoChain(subtype)
            ? resolveJsonbTypeInfoPropertyOrder(subtype, finalTypePropertyDescriptors)
            : List.of();

        subtype.annotate(SerdeConfig.class, builder -> {
            builder.member(SerdeConfig.TYPE_NAME, allNames.get(0));
            builder.member(SerdeConfig.TYPE_NAMES, allNames.toArray(new String[0]));

            if (optionalDiscriminatorType.isPresent()) {
                // Discriminator type might be missing if JsonTypeInfo is defined on the argument
                SerdeConfig.SerSubtyped.DiscriminatorType discriminatorType = optionalDiscriminatorType.get();
                switch (discriminatorType) {
                    case WRAPPER_OBJECT ->
                        builder.member(SerdeConfig.WRAPPER_PROPERTY, allNames.get(0));
                    case WRAPPER_ARRAY ->
                        builder.member(SerdeConfig.ARRAY_WRAPPER_PROPERTY, allNames.get(0));
                    case PROPERTY ->
                        builder.member(SerdeConfig.TYPE_PROPERTY, finalTypePropertyDescriptors.get(finalTypePropertyDescriptors.size() - 1).propertyName());
                    case EXISTING_PROPERTY ->
                        builder.member(SerdeConfig.TYPE_DISCRIMINATOR_TYPE, discriminatorType);
                }
            }
            if (finalTypePropertyDescriptors.size() > 1) {
                builder.member(SerdeConfig.TYPE_PROPERTIES, finalTypePropertyDescriptors.stream().map(TypePropertyDescriptor::propertyName).toArray(String[]::new));
                builder.member(SerdeConfig.TYPE_PROPERTY_VALUES, finalTypePropertyDescriptors.stream().map(TypePropertyDescriptor::propertyValue).toArray(String[]::new));
            }

            if (supertype.booleanValue(SerdeConfig.SerSubtyped.class, SerdeConfig.SerSubtyped.DISCRIMINATOR_VISIBLE).orElse(false)) {
                builder.member(SerdeConfig.TYPE_PROPERTY_VISIBLE, true);
            }
        });
        if (!jsonbTypeInfoPropertyOrder.isEmpty()) {
            subtype.annotate(SerdeConfig.META_ANNOTATION_PROPERTY_ORDER, builder ->
                builder.values(jsonbTypeInfoPropertyOrder.toArray(new String[0]))
            );
        }
    }

    private boolean isJsonbTypeInfoChain(ClassElement element) {
        return findTypeInfoChain(element).stream()
            .anyMatch(typeInfoType -> typeInfoType.booleanValue(SerdeConfig.SerSubtyped.class, SerdeConfig.SerSubtyped.JSONB_TYPE_INFO).orElse(false));
    }

    @SuppressWarnings("java:S3776")
    private List<String> resolveJsonbTypeInfoPropertyOrder(ClassElement subtype, List<TypePropertyDescriptor> typePropertyDescriptors) {
        List<String> order = new ArrayList<>();
        typePropertyDescriptors.stream()
            .map(TypePropertyDescriptor::propertyName)
            .forEach(order::add);
        List<ClassElement> hierarchy = findTypeInfoChain(subtype);
        if (hierarchy.stream().noneMatch(type -> type.getName().equals(subtype.getName()))) {
            hierarchy.add(subtype);
        }
        for (ClassElement type : hierarchy) {
            for (FieldElement field : type.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyInstance().onlyDeclared().modifiers(modifiers -> modifiers.contains(ElementModifier.PUBLIC)))) {
                addIfAbsent(order, resolvePropertyName(field));
            }
            for (MethodElement method : type.getEnclosedElements(ElementQuery.ALL_METHODS.onlyInstance().onlyDeclared())) {
                if (!method.hasParameters() && !method.getReturnType().isVoid()) {
                    String methodName = method.getName();
                    if (methodName.startsWith("get") && methodName.length() > 3) {
                        addIfAbsent(order, NameUtils.decapitalize(methodName.substring(3)));
                    } else if (methodName.startsWith("is") && methodName.length() > 2) {
                        addIfAbsent(order, NameUtils.decapitalize(methodName.substring(2)));
                    }
                }
            }
        }
        return order;
    }

    private void addIfAbsent(List<String> values, String value) {
        if (!values.contains(value)) {
            values.add(value);
        }
    }

    private List<TypePropertyDescriptor> resolveTypePropertyDescriptors(ClassElement subtype,
                                                                       ClassElement directSupertype,
                                                                       String directTypeProperty,
                                                                       String directTypeName) {
        List<ClassElement> typeInfoTypes = findTypeInfoChain(subtype);
        List<TypePropertyDescriptor> descriptors = new ArrayList<>(typeInfoTypes.size());
        for (ClassElement typeInfoType : typeInfoTypes) {
            boolean directTypeInfo = typeInfoType.getName().equals(directSupertype.getName());
            String typeProperty = directTypeInfo
                ? directTypeProperty
                : typeInfoType.stringValue(SerdeConfig.SerSubtyped.class, SerdeConfig.SerSubtyped.DISCRIMINATOR_PROP).orElse(null);
            if (typeProperty == null) {
                continue;
            }
            String typeName = directTypeInfo
                ? directTypeName
                : resolveSubtypeName(typeInfoType, subtype).orElse(null);
            if (typeName != null) {
                descriptors.add(new TypePropertyDescriptor(typeProperty, typeName));
            }
        }
        if (descriptors.isEmpty()) {
            descriptors.add(new TypePropertyDescriptor(directTypeProperty, directTypeName));
        }
        return descriptors;
    }

    private List<ClassElement> findTypeInfoChain(ClassElement element) {
        List<ClassElement> typeInfoTypes = new ArrayList<>();
        collectTypeInfoTypes(element, typeInfoTypes, new HashSet<>());
        typeInfoTypes.sort((left, right) -> {
            if (left.equals(right)) {
                return 0;
            }
            if (left.isAssignable(right)) {
                return 1;
            }
            if (right.isAssignable(left)) {
                return -1;
            }
            return left.getName().compareTo(right.getName());
        });
        return typeInfoTypes;
    }

    private void collectTypeInfoTypes(ClassElement element, List<ClassElement> typeInfoTypes, Set<String> seen) {
        if (!seen.add(element.getName())) {
            return;
        }
        if (element.hasDeclaredAnnotation(SerdeConfig.SerSubtyped.class)
            && getDiscriminatorType(element).orElse(null) == SerdeConfig.SerSubtyped.DiscriminatorType.PROPERTY) {
            typeInfoTypes.add(element);
        }
        for (ClassElement anInterface : element.getInterfaces()) {
            collectTypeInfoTypes(anInterface, typeInfoTypes, seen);
        }
        element.getSuperType().ifPresent(superType -> collectTypeInfoTypes(superType, typeInfoTypes, seen));
    }

    private Optional<String> resolveSubtypeName(ClassElement typeInfoType, ClassElement subtype) {
        for (AnnotationValue<SerdeConfig.SerSubtyped.SerSubtype> parentSubtype : typeInfoType.getDeclaredAnnotationValuesByType(SerdeConfig.SerSubtyped.SerSubtype.class)) {
            Optional<AnnotationClassValue<?>> annotationClassValue = parentSubtype.annotationClassValue(AnnotationMetadata.VALUE_MEMBER);
            if (annotationClassValue.isEmpty()) {
                continue;
            }
            if (isTypeInHierarchy(subtype, annotationClassValue.get().getName(), new HashSet<>())) {
                Optional<String> name = parentSubtype.stringValue("name");
                if (name.isPresent()) {
                    return name;
                }
                String[] names = parentSubtype.stringValues("names");
                if (names.length > 0) {
                    return Optional.of(names[0]);
                }
            }
        }
        return Optional.empty();
    }

    private boolean isTypeInHierarchy(ClassElement element, String typeName, Set<String> seen) {
        if (!seen.add(element.getName())) {
            return false;
        }
        if (element.getName().equals(typeName)) {
            return true;
        }
        for (ClassElement anInterface : element.getInterfaces()) {
            if (isTypeInHierarchy(anInterface, typeName, seen)) {
                return true;
            }
        }
        return element.getSuperType().map(superType -> isTypeInHierarchy(superType, typeName, seen)).orElse(false);
    }

    private void visitClassInternal(ClassElement element, VisitorContext context, boolean isImport) {
        visitClassSubtypes(element, context);

        if (element.hasDeclaredAnnotation(SerdeImport.Repeated.class) && !isImport) {
            final List<AnnotationValue<SerdeImport>> values = element.getDeclaredAnnotationValuesByType(SerdeImport.class);
            List<AnnotationClassValue<?>> classValues = new ArrayList<>();
            for (AnnotationValue<SerdeImport> value : values) {
                value.annotationClassValue(AnnotationMetadata.VALUE_MEMBER)
                        .flatMap(acv -> context.getClassElement(acv.getName()))
                        .ifPresent(c -> {
                            if (!c.isPublic()) {
                                throw new ProcessingException(element, "Cannot mixin non-public type: " + c.getName());
                            } else {
                                handleClassImport(context, value, c, classValues);
                            }
                        });
                 value.stringValue("packageName").ifPresent(packageName -> {
                     ClassElement[] classElements = context.getClassElements(packageName, "*");
                     for (ClassElement c : classElements) {
                         if (c.isPublic() && !c.isInner()) {
                             handleClassImport(context, value, c, classValues);
                         }
                     }
                 });
            }
            element.annotate(Introspected.class, builder ->
                builder.member("classes", classValues.toArray(new AnnotationClassValue[0]))
            );
        } else if (isSerdeAnnotated(element) || isImport) {
            if (!element.hasStereotype(Serdeable.Serializable.class) &&
                    !element.hasStereotype(Serdeable.Deserializable.class) && !isImport) {
                element.annotate(Serdeable.class);
                element.annotate(Introspected.class, i -> {
                    i.member("accessKind", Introspected.AccessKind.METHOD, Introspected.AccessKind.FIELD);
                    i.member(VISIBILITY, Introspected.Visibility.PUBLIC);
                });
            }

            AnnotationValue<SerdeConfig> declaredAnnotation = element.getDeclaredAnnotation(SerdeConfig.class);
            if (failOnError && declaredAnnotation != null) {
                String serializeAs = declaredAnnotation.stringValue(SerdeConfig.SERIALIZE_AS).orElse(null);
                if (serializeAs != null) {
                    ClassElement thatType = context.getClassElement(serializeAs).orElse(null);
                    if (thatType != null && !thatType.isAssignable(element) && failOnError) {
                        throw new ProcessingException(element, "Type to serialize as [" + serializeAs + "], must be a subtype of the annotated type: " + element.getName());
                    }
                }

                String deserializeAs = declaredAnnotation.stringValue(SerdeConfig.DESERIALIZE_AS).orElse(null);
                if (deserializeAs != null) {
                    ClassElement thatType = context.getClassElement(deserializeAs).orElse(null);
                    if (thatType != null && !thatType.isAssignable(element) && failOnError) {
                        throw new ProcessingException(element, "Type to deserialize as [" + deserializeAs + "], must be a subtype of the annotated type: " + element.getName());
                    }
                }
            }

            final MethodElement primaryConstructor = element.getPrimaryConstructor().orElse(null);
            if (primaryConstructor != null) {

                this.creatorMode = primaryConstructor.enumValue(Creator.class, "mode", SerdeConfig.SerCreatorMode.class)
                    .orElse(SerdeConfig.SerCreatorMode.PROPERTIES);
                if (creatorMode == SerdeConfig.SerCreatorMode.DELEGATING) {
                    if (failOnError && primaryConstructor.getParameters().length != 1) {
                        throw new ProcessingException(element, "DELEGATING creator mode requires exactly one Creator parameter, but more were defined.");
                    }
                }
            }

            inheritPackageFormat(element);
            inheritPackageInclude(element);
            applyJaxbRootDefault(element);
            visitProperties(element, context);

            findTypeInfo(element, false)
                .ifPresent(superType -> visitSubtype(superType, element, context));

            if (failOnError && element.hasDeclaredAnnotation(SerdeConfig.SerSubtyped.class) && creatorMode == SerdeConfig.SerCreatorMode.DELEGATING) {
                throw new ProcessingException(element, "Inheritance cannot be combined with DELEGATING creation");
            }
        }

        applySourceGenDecision(element);
    }

    private void applyJaxbRootDefault(ClassElement element) {
        if (!element.hasAnnotation(JAXB_XML_ROOT_ELEMENT)
            || element.hasAnnotation("tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement")
            || element.hasAnnotation("com.fasterxml.jackson.annotation.JsonRootName")) {
            return;
        }
        String rootName = element.stringValue(JAXB_XML_ROOT_ELEMENT, "name").orElse("##default");
        if ("##default".equals(rootName)) {
            String simpleName = element.getSimpleName();
            int nestedSeparator = simpleName.lastIndexOf('$');
            if (nestedSeparator >= 0) {
                simpleName = simpleName.substring(nestedSeparator + 1);
            }
            String finalSimpleName = simpleName;
            element.annotate(SerdeConfig.class, builder -> builder.member(SerdeConfig.WRAPPER_PROPERTY, NameUtils.decapitalize(finalSimpleName)));
        }
    }

    private void inheritPackageFormat(ClassElement element) {
        AnnotationMetadata packageMetadata = element.getPackage().getAnnotationMetadata();
        if (!packageMetadata.hasAnnotation(SerdeConfig.class) || FormatConfiguration.from(element.getAnnotationMetadata()) != null) {
            return;
        }
        element.annotate(SerdeConfig.class, builder -> {
            packageMetadata.stringValue(SerdeConfig.class, SerdeConfig.PATTERN)
                .ifPresent(pattern -> builder.member(SerdeConfig.PATTERN, pattern));
            packageMetadata.enumValue(SerdeConfig.class, SerdeConfig.SHAPE, FormatConfiguration.Shape.class)
                .ifPresent(shape -> builder.member(SerdeConfig.SHAPE, shape));
            packageMetadata.stringValue(SerdeConfig.class, SerdeConfig.LOCALE)
                .ifPresent(locale -> builder.member(SerdeConfig.LOCALE, locale));
            packageMetadata.stringValue(SerdeConfig.class, SerdeConfig.TIMEZONE)
                .ifPresent(timezone -> builder.member(SerdeConfig.TIMEZONE, timezone));
            packageMetadata.booleanValue(SerdeConfig.class, SerdeConfig.LENIENT)
                .ifPresent(lenient -> builder.member(SerdeConfig.LENIENT, lenient));
            packageMetadata.intValue(SerdeConfig.class, SerdeConfig.RADIX)
                .ifPresent(radix -> builder.member(SerdeConfig.RADIX, radix));
        });
    }

    private void inheritPackageInclude(ClassElement element) {
        AnnotationMetadata packageMetadata = element.getPackage().getAnnotationMetadata();
        if (!packageMetadata.hasAnnotation(SerdeConfig.class) ||
            element.enumValue(SerdeConfig.class, SerdeConfig.INCLUDE, SerdeConfig.SerInclude.class).isPresent()) {
            return;
        }
        packageMetadata.enumValue(SerdeConfig.class, SerdeConfig.INCLUDE, SerdeConfig.SerInclude.class)
            .ifPresent(include -> element.annotate(SerdeConfig.class, builder -> builder.member(SerdeConfig.INCLUDE, include)));
    }

    private void applySourceGenDecision(ClassElement element) {
        SimpleSerdeShapeDecision decision = sourceGenShapeAnalyzer.analyze(element);
        validateRequiredGeneratedSerde(element, decision);
        element.annotate(SerdeConfig.class, builder -> {
            builder.member(SerdeConfig.SOURCEGEN_SHAPE, decision.shapeKind().name());
            builder.member(SerdeConfig.SOURCEGEN_SERIALIZER_ELIGIBLE, decision.serializerEligible());
            builder.member(SerdeConfig.SOURCEGEN_DESERIALIZER_ELIGIBLE, decision.deserializerEligible());
            if (!decision.serializerFallbackReasons().isEmpty()) {
                builder.member(SerdeConfig.SOURCEGEN_SERIALIZER_FALLBACK_REASONS, decision.serializerFallbackReasons().keySet().stream()
                    .map(Enum::name)
                    .toArray(String[]::new));
            }
            if (!decision.deserializerFallbackReasons().isEmpty()) {
                builder.member(SerdeConfig.SOURCEGEN_DESERIALIZER_FALLBACK_REASONS, decision.deserializerFallbackReasons().keySet().stream()
                    .map(Enum::name)
                    .toArray(String[]::new));
            }
            if (decision.serializerEligible()) {
                builder.member(SerdeConfig.SOURCEGEN_SERIALIZER_CLASS, SerdeSourceGenClassNaming.generatedSerializerClassName(element));
            }
            if (decision.deserializerEligible()) {
                builder.member(SerdeConfig.SOURCEGEN_DESERIALIZER_CLASS, SerdeSourceGenClassNaming.generatedDeserializerClassName(element));
            }
        });
    }

    private void validateRequiredGeneratedSerde(ClassElement element, SimpleSerdeShapeDecision decision) {
        if (!element.hasAnnotation(SerdeableGenerated.class)
            || !element.booleanValue(SerdeableGenerated.class, "required").orElse(true)
            || element.booleanValue(SerdeableGenerated.class, "skip").orElse(false)) {
            return;
        }
        if (!element.booleanValue(SerdeableGenerated.class, "skipSerializer").orElse(false) && !decision.serializerEligible()) {
            throw new ProcessingException(element, "Source-generated serializer required for " + element.getName()
                + " but generation is not supported. Fallback reasons: " + String.join("; ", decision.serializerFallbackReasons().values()));
        }
        if (!element.booleanValue(SerdeableGenerated.class, "skipDeserializer").orElse(false) && !decision.deserializerEligible()) {
            throw new ProcessingException(element, "Source-generated deserializer required for " + element.getName()
                + " but generation is not supported. Fallback reasons: " + String.join("; ", decision.deserializerFallbackReasons().values()));
        }
    }

    private void visitProperties(ClassElement classElement, VisitorContext context) {
        final List<PropertyElement> beanProperties = classElement.getBeanProperties();
        ignoreUnannotatedJaxbNoneProperties(classElement, beanProperties);
        validateJaxbXmlValues(classElement, beanProperties);
        applyJaxbElementRefDefaults(beanProperties);
        applyJaxbIdProperties(beanProperties);
        applyJsonIdentityInfoProperties(classElement, beanProperties);
        applyJsonIdentityReferenceProperties(beanProperties);
        applyJaxbCollectionDefaults(classElement, beanProperties);
        final List<String> order;
        if (classElement.booleanValue(SerdeConfig.META_ANNOTATION_PROPERTY_ORDER, "alphabetic").orElse(false)) {
            List<String> newOrder = beanProperties.stream()
                .map(this::resolvePropertyName)
                .sorted()
                .toList();
            classElement.annotate(SerdeConfig.META_ANNOTATION_PROPERTY_ORDER, b -> b.values(newOrder.toArray(new String[0])));
            order = prioritizeXmlAttributeProperties(beanProperties, newOrder);
        } else {
            order = prioritizeXmlAttributeProperties(
                beanProperties,
                List.of(classElement.stringValues(SerdeConfig.META_ANNOTATION_PROPERTY_ORDER))
            );
        }
        Collections.reverse(order);
        final Set<Introspected.AccessKind> access = CollectionUtils.setOf(classElement.enumValues(Introspected.class,
                                                                                             "accessKind",
                                                                                             Introspected.AccessKind.class));
        boolean supportFields = access.contains(Introspected.AccessKind.FIELD);
        final String[] ignoresProperties = classElement.stringValues(SerdeConfig.SerIgnored.class);
        final String[] includeProperties = classElement.stringValues(SerdeConfig.SerIncluded.class);

        final boolean ignoreOnlyDeserialization = classElement.booleanValue(SerdeConfig.SerIgnored.class, SerdeConfig.SerIgnored.ALLOW_SERIALIZE).orElse(false);
        final boolean ignoreOnlySerialization = classElement.booleanValue(SerdeConfig.SerIgnored.class, SerdeConfig.SerIgnored.ALLOW_DESERIALIZE).orElse(false);
        PropertyNamingStrategy propertyNamingStrategy = getPropertyNamingStrategy(classElement, null);
        processProperties(
            context,
                beanProperties,
                order,
                ignoresProperties,
                includeProperties,
                ignoreOnlyDeserialization,
                ignoreOnlySerialization,
                propertyNamingStrategy
        );
        if (supportFields) {
            final List<FieldElement> fields = classElement.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyInstance()
                                                                                            .onlyAccessible());
            processProperties(
                context,
                    fields,
                    order,
                    ignoresProperties,
                    includeProperties,
                    ignoreOnlyDeserialization,
                    ignoreOnlySerialization,
                    propertyNamingStrategy
            );
        }
    }

    private void ignoreUnannotatedJaxbNoneProperties(ClassElement classElement, List<PropertyElement> beanProperties) {
        AnnotationValue<?> accessType = classElement.getDeclaredAnnotation(JAXB_XML_ACCESSOR_TYPE);
        if (accessType == null || !accessType.stringValue("value").filter(value -> value.endsWith("NONE")).isPresent()) {
            return;
        }
        for (PropertyElement property : beanProperties) {
            if (!hasJaxbBindingAnnotation(property)) {
                ignoreProperty(false, false, property);
            }
        }
    }

    private boolean hasJaxbBindingAnnotation(PropertyElement property) {
        return property.getAnnotationNames().stream().anyMatch(name -> name.startsWith(JAXB_ANNOTATION_PREFIX))
            || property.getReadMethod().map(this::hasJaxbBindingAnnotation).orElse(false)
            || property.getWriteMethod().map(this::hasJaxbBindingAnnotation).orElse(false)
            || property.getField().map(this::hasJaxbBindingAnnotation).orElse(false);
    }

    private boolean hasJaxbBindingAnnotation(Element element) {
        return element.getAnnotationNames().stream().anyMatch(name -> name.startsWith(JAXB_ANNOTATION_PREFIX));
    }

    private void validateJaxbXmlValues(ClassElement classElement, List<PropertyElement> beanProperties) {
        long valueProperties = beanProperties.stream().filter(property -> property.hasAnnotation(JAXB_XML_VALUE)).count();
        if (valueProperties > 1) {
            throw new ProcessingException(classElement, "Only a single XmlValue property is supported");
        }
    }

    private void applyJaxbElementRefDefaults(List<PropertyElement> beanProperties) {
        for (PropertyElement property : beanProperties) {
            AnnotationValue<?> elementRef = property.getAnnotation(JAXB_XML_ELEMENT_REF);
            if (elementRef == null || elementRef.annotationClassValue("type")
                .filter(type -> "jakarta.xml.bind.annotation.XmlElementRef$DEFAULT".equals(type.getName()))
                .isEmpty()) {
                continue;
            }
            ClassElement referencedType = resolveRefType(property.getGenericType());
            AnnotationValue<?> rootElement = referencedType.getAnnotation(JAXB_XML_ROOT_ELEMENT);
            String name = elementRef.stringValue("name").filter(value -> !"##default".equals(value))
                .orElseGet(() -> rootElement == null ? NameUtils.decapitalize(referencedType.getSimpleName())
                    : rootElement.stringValue("name").filter(value -> !"##default".equals(value))
                        .orElseGet(() -> NameUtils.decapitalize(referencedType.getSimpleName())));
            AnnotationValue<SerdeConfig.SerSubtyped.SerSubtype> subtype = AnnotationValue
                .builder(SerdeConfig.SerSubtyped.SerSubtype.class)
                .member(AnnotationMetadata.VALUE_MEMBER, new AnnotationClassValue<>(referencedType.getName()))
                .member("names", new String[] {name})
                .build();
            property.annotate(SerdeConfig.SerUnwrapped.class);
            property.annotate(SerdeConfig.SerSubtyped.class, builder -> builder
                .values(subtype)
                .member(SerdeConfig.SerSubtyped.DISCRIMINATOR_TYPE, SerdeConfig.SerSubtyped.DiscriminatorType.WRAPPER_OBJECT)
                .member(SerdeConfig.SerSubtyped.DISCRIMINATOR_VALUE, SerdeConfig.SerSubtyped.DiscriminatorValueKind.NAME));
        }
    }

    private void applyJaxbIdProperties(List<PropertyElement> beanProperties) {
        for (PropertyElement property : beanProperties) {
            if (hasJaxbPropertyAnnotation(property, JAXB_XML_ID)) {
                property.annotate(SerdeConfig.SerManagedRef.class, builder ->
                    builder.member(SerdeConfig.SerManagedRef.SCOPE, SerdeConfig.SerManagedRef.Scope.DOCUMENT));
            }
            if (hasJaxbPropertyAnnotation(property, JAXB_XML_ID_REF)) {
                property.annotate(SerdeConfig.class, builder -> builder.member(SerdeConfig.ID_REFERENCE, SerdeConfig.IdReference.ALWAYS_ID));
            }
        }
    }

    private void applyJsonIdentityInfoProperties(ClassElement classElement, List<PropertyElement> beanProperties) {
        AnnotationValue<?> identityInfo = classElement.getAnnotation(JACKSON_IDENTITY_INFO);
        if (identityInfo == null) {
            return;
        }
        String generator = identityInfo.annotationClassValue("generator").map(AnnotationClassValue::getName).orElse(null);
        if (!JACKSON_PROPERTY_GENERATOR.equals(generator)) {
            throw new ProcessingException(classElement, "JsonIdentityInfo only supports ObjectIdGenerators.PropertyGenerator");
        }
        String scope = identityInfo.annotationClassValue("scope").map(AnnotationClassValue::getName).orElse(null);
        if (scope != null && !Object.class.getName().equals(scope)) {
            throw new ProcessingException(classElement, "JsonIdentityInfo member [scope] is not supported");
        }
        String resolver = identityInfo.annotationClassValue("resolver").map(AnnotationClassValue::getName).orElse(null);
        if (resolver != null && !JACKSON_SIMPLE_OBJECT_ID_RESOLVER.equals(resolver)) {
            throw new ProcessingException(classElement, "JsonIdentityInfo member [resolver] is not supported");
        }
        String propertyName = identityInfo.stringValue("property").orElse("@id");
        PropertyElement property = beanProperties.stream()
            .filter(candidate -> propertyName.equals(resolvePropertyName(candidate)))
            .findFirst()
            .orElseThrow(() -> new ProcessingException(classElement,
                "JsonIdentityInfo property [" + propertyName + "] does not match a bean property"));
        for (PropertyElement candidate : beanProperties) {
            if (candidate != property && isDocumentIdProperty(candidate)) {
                throw new ProcessingException(classElement, "JsonIdentityInfo property [" + propertyName
                    + "] conflicts with the XmlID property [" + resolvePropertyName(candidate) + "]");
            }
        }
        // The identity property is the document-scoped identifier of the bean, like a JAXB XmlID,
        // and additionally carries object identity semantics
        property.annotate(SerdeConfig.SerManagedRef.class, builder ->
            builder.member(SerdeConfig.SerManagedRef.SCOPE, SerdeConfig.SerManagedRef.Scope.DOCUMENT));
        property.annotate(SerdeConfig.class, builder -> builder.member(SerdeConfig.OBJECT_IDENTITY, true));
    }

    private boolean isDocumentIdProperty(PropertyElement property) {
        return isDocumentIdElement(property)
            || property.getReadMethod().map(this::isDocumentIdElement).orElse(false)
            || property.getWriteMethod().map(this::isDocumentIdElement).orElse(false)
            || property.getField().map(this::isDocumentIdElement).orElse(false);
    }

    private boolean isDocumentIdElement(Element element) {
        return element.enumValue(SerdeConfig.SerManagedRef.class, SerdeConfig.SerManagedRef.SCOPE,
            SerdeConfig.SerManagedRef.Scope.class).orElse(null) == SerdeConfig.SerManagedRef.Scope.DOCUMENT;
    }

    /**
     * Marks properties that reference Jackson object identities: a property that must always be written as the
     * identifier uses the identifier reference serdes (like a JAXB XmlIDREF), a property whose type declares an
     * object identity may hold the object or its identifier. Collections of identity types are handled by the
     * element type's own serde.
     */
    private void applyJsonIdentityReferenceProperties(List<PropertyElement> beanProperties) {
        for (PropertyElement property : beanProperties) {
            ClassElement propertyType = property.getGenericType();
            boolean identityType = propertyType.hasAnnotation(JACKSON_IDENTITY_INFO);
            boolean identityElementType = identityType || resolveRefType(propertyType).hasAnnotation(JACKSON_IDENTITY_INFO);
            AnnotationValue<?> identityReference = findPropertyAnnotation(property, JACKSON_IDENTITY_REFERENCE);
            if (identityReference != null && !identityElementType) {
                throw new ProcessingException(property, "JsonIdentityReference requires a property type annotated with JsonIdentityInfo");
            }
            boolean alwaysAsId = identityReference != null && identityReference.booleanValue("alwaysAsId").orElse(false);
            if (alwaysAsId) {
                property.annotate(SerdeConfig.class, builder -> builder
                    .member(SerdeConfig.ID_REFERENCE, SerdeConfig.IdReference.ALWAYS_ID)
                    .member(SerdeConfig.SERIALIZER_CLASS, new AnnotationClassValue<>(ID_REFERENCE_SERIALIZER))
                    .member(SerdeConfig.DESERIALIZER_CLASS, new AnnotationClassValue<>(ID_REFERENCE_DESERIALIZER)));
            } else if (identityType) {
                property.annotate(SerdeConfig.class, builder -> builder.member(SerdeConfig.ID_REFERENCE, SerdeConfig.IdReference.OBJECT_OR_ID));
            }
        }
    }

    private static @Nullable AnnotationValue<?> findPropertyAnnotation(PropertyElement property, String annotation) {
        AnnotationValue<?> value = property.getAnnotation(annotation);
        if (value == null) {
            value = property.getReadMethod().map(method -> method.getAnnotation(annotation)).orElse(null);
        }
        if (value == null) {
            value = property.getWriteMethod().map(method -> method.getAnnotation(annotation)).orElse(null);
        }
        if (value == null) {
            value = property.getField().map(field -> field.getAnnotation(annotation)).orElse(null);
        }
        return value;
    }

    private boolean hasJaxbPropertyAnnotation(PropertyElement property, String annotation) {
        return property.hasAnnotation(annotation)
            || property.getReadMethod().map(method -> method.hasAnnotation(annotation)).orElse(false)
            || property.getWriteMethod().map(method -> method.hasAnnotation(annotation)).orElse(false)
            || property.getField().map(field -> field.hasAnnotation(annotation)).orElse(false);
    }

    private void applyJaxbCollectionDefaults(ClassElement classElement, List<PropertyElement> beanProperties) {
        if (!isJaxbAutoBindable(classElement)) {
            return;
        }
        for (PropertyElement property : beanProperties) {
            ClassElement type = property.getGenericType();
            if (property.hasAnnotation(JAXB_XML_ELEMENT_WRAPPER)
                && property.stringValue(JAXB_XML_ELEMENT_WRAPPER, "name").filter("##default"::equals).isPresent()) {
                property.annotate(SerdeConfig.class, builder -> builder.member(SerdeConfig.WRAPPER_PROPERTY, property.getName()));
            } else if ((type.isArray() || type.isAssignable(Collection.class))
                && !property.hasAnnotation(JAXB_XML_ELEMENT_WRAPPER)
                && !property.hasAnnotation("tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper")) {
                property.annotate(SerdeConfig.class, builder -> builder.member(SerdeConfig.META_ANNOTATION_PROPERTY, false));
            }
        }
    }

    private List<String> prioritizeXmlAttributeProperties(List<PropertyElement> beanProperties, List<String> baseOrder) {
        boolean hasXmlAttributes = beanProperties.stream().anyMatch(this::isXmlAttributeProperty);
        if (!hasXmlAttributes) {
            return new ArrayList<>(baseOrder);
        }
        List<String> prioritizedOrder = new ArrayList<>(baseOrder.size() + beanProperties.size());
        List<String> prioritizedAttributes = new ArrayList<>(baseOrder.size());
        List<String> prioritizedNonAttributes = new ArrayList<>(baseOrder.size());
        for (String configuredName : baseOrder) {
            PropertyElement propertyElement = findProperty(beanProperties, configuredName);
            if (propertyElement != null && isXmlAttributeProperty(propertyElement)) {
                prioritizedAttributes.add(configuredName);
            } else {
                prioritizedNonAttributes.add(configuredName);
            }
        }
        prioritizedOrder.addAll(prioritizedAttributes);
        prioritizedOrder.addAll(prioritizedNonAttributes);

        List<String> remainingAttributes = new ArrayList<>(beanProperties.size());
        List<String> remainingNonAttributes = new ArrayList<>(beanProperties.size());
        for (PropertyElement beanProperty : beanProperties) {
            String propertyName = resolvePropertyName(beanProperty);
            if (prioritizedOrder.contains(propertyName) || prioritizedOrder.contains(beanProperty.getName())) {
                continue;
            }
            if (isXmlAttributeProperty(beanProperty)) {
                remainingAttributes.add(propertyName);
            } else {
                remainingNonAttributes.add(propertyName);
            }
        }
        prioritizedOrder.addAll(remainingAttributes);
        prioritizedOrder.addAll(remainingNonAttributes);
        return prioritizedOrder;
    }

    @Nullable
    private PropertyElement findProperty(List<PropertyElement> beanProperties, String configuredName) {
        for (PropertyElement beanProperty : beanProperties) {
            if (configuredName.equals(beanProperty.getName()) || configuredName.equals(resolvePropertyName(beanProperty))) {
                return beanProperty;
            }
        }
        return null;
    }

    private boolean isXmlAttributeProperty(PropertyElement beanProperty) {
        return hasXmlAttributeProperty(beanProperty)
            || beanProperty.getReadMethod().map(this::hasXmlAttributeProperty).orElse(false)
            || beanProperty.getWriteMethod().map(this::hasXmlAttributeProperty).orElse(false);
    }

    private boolean hasXmlAttributeProperty(Element beanProperty) {
        return beanProperty.booleanValue(SerdeConfig.class, SerdeConfig.XML_ATTRIBUTE_PROPERTY).orElse(false);
    }

    private void handleClassImport(VisitorContext context,
                                   AnnotationValue<SerdeImport> value,
                                   ClassElement type,
                                   List<AnnotationClassValue<?>> classValues) {
        classValues.add(new AnnotationClassValue<>(type.getName()));
        final ClassElement mixinType = value.stringValue("mixin").flatMap(context::getClassElement)
                .orElse(null);
        if (value.booleanValue("serializable").orElse(true)) {
            type.annotate(Serdeable.Serializable.class);
        }
        if (value.booleanValue("deserializable").orElse(true)) {
            type.annotate(Serdeable.Deserializable.class);
        }
        if (mixinType != null) {
            visitMixin(mixinType, type, context);
            sanitizeCoreJsonPropertyFieldAnnotations(type);
            ensureDefaultIntrospected(type, false);
        } else {
            sanitizeCoreJsonPropertyFieldAnnotations(type);
            ensureDefaultIntrospected(type);
            visitClassInternal(type, context, true);
            ensureDefaultIntrospected(type);
        }
        AnnotationValue<Annotation> jsonPojoAnn = type.getAnnotation("tools.jackson.databind.annotation.JsonPOJOBuilder");
        if (jsonPojoAnn != null) {
            String buildMethod = jsonPojoAnn.stringValue("buildMethodName").orElse("build");
            type.getEnclosedElement(ElementQuery.ALL_METHODS.named(n -> n.equals(buildMethod)))
                .ifPresent(m -> m.annotate(Executable.class));
        }
    }

    private void ensureDefaultIntrospected(ClassElement type) {
        ensureDefaultIntrospected(type, true);
    }

    private void ensureDefaultIntrospected(ClassElement type, boolean includeFields) {
        if (!type.hasAnnotation(Introspected.class)) {
            type.annotate(Introspected.class, i -> {
                if (includeFields) {
                    i.member("accessKind", Introspected.AccessKind.METHOD, Introspected.AccessKind.FIELD);
                } else {
                    i.member("accessKind", Introspected.AccessKind.METHOD);
                }
                i.member(VISIBILITY, Introspected.Visibility.PUBLIC);
            });
        }
    }

    private void sanitizeCoreJsonPropertyFieldAnnotations(ClassElement type) {
        for (FieldElement field : type.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyInstance())) {
            sanitizeCoreJsonPropertyFieldAnnotation(field);
        }
    }

    private void sanitizeCoreJsonPropertyFieldAnnotation(FieldElement field) {
        if (!field.isPublic()
            && field.hasAnnotation("com.fasterxml.jackson.annotation.JsonProperty")
            && field.hasAnnotation(Introspected.Property.class)
            && field.getOwningType()
                .enumValue(Introspected.class, VISIBILITY, Introspected.Visibility.class)
                .filter(Introspected.Visibility.ANY::equals)
                .isEmpty()) {
            // Micronaut Core maps @JsonProperty to @Introspected.Property.
            // Keep Jackson metadata for Serde, but don't force inaccessible field access.
            field.removeAnnotation(Introspected.Property.class);
        }
    }

    private void visitMixin(ClassElement mixinType, ClassElement type,  VisitorContext context) {
        AnnotationValue<Introspected> introspectedAnnotation = mixinType.getAnnotation(Introspected.class);
        if (introspectedAnnotation != null) {
            type.annotate(introspectedAnnotation);
            // We don't need to introspect the mixin
            mixinType.removeAnnotation(Introspected.class);
        }
        mixinType.getAnnotationNames()
                .stream().filter(n -> n.startsWith("io.micronaut.serde"))
                .forEach(n -> {
                    final AnnotationValue<Annotation> ann = mixinType.getAnnotation(n);
                    if (ann != null && !ann.getAnnotationName().equals(SerdeImport.class.getName())) {
                        type.annotate(ann);
                    }
                });
        mixinType.findAnnotation(SerdeConfig.class).ifPresent(type::annotate);
        final Map<String, FieldElement> serdeFields = mixinType.getEnclosedElements(
                ElementQuery.ALL_FIELDS
                        .onlyInstance()
                        .onlyDeclared()
        ).stream().collect(Collectors.toMap(
                FieldElement::getName,
                (e) -> e
        ));

        final MethodElement mixinCtor = mixinType.getPrimaryConstructor().orElse(null);
        MethodElement targetCtor = type.getPrimaryConstructor().orElse(null);
        if (mixinCtor != null && targetCtor != null) {
            if (!argumentsMatch(mixinCtor, targetCtor)) {
                // The mixin constructor and the primary constructor doesn't match,
                // lets try to find a matching one and mark it as a primary
                MethodElement prevCtor = targetCtor;
                targetCtor = type.getAccessibleConstructors().stream().filter(c -> argumentsMatch(mixinCtor, c)).findFirst().orElse(null);
                if (targetCtor != null) {
                    targetCtor.annotate(Creator.class);
                    prevCtor.removeAnnotation(Creator.class);
                }
            }
            if (targetCtor != null) {
                replicateAnnotations(mixinCtor, targetCtor);
                ParameterElement[] mixinCtorParameters = mixinCtor.getParameters();
                ParameterElement[] targetCtorParameters = targetCtor.getParameters();
                for (int i = 0; i < mixinCtorParameters.length; i++) {
                    ParameterElement mixinCtorParameter = mixinCtorParameters[i];
                    ParameterElement targetCtorParameter = targetCtorParameters[i];
                    replicateAnnotations(mixinCtorParameter, targetCtorParameter);
                }
            }
        }

        final List<MethodElement> serdeMethods = mixinType.isRecord() ? Collections.emptyList() : new ArrayList<>(mixinType.getEnclosedElements(
                ElementQuery.ALL_METHODS
                        .onlyInstance()
                        .onlyDeclared()
                        .annotated((ann) -> ann.getAnnotationNames().stream().anyMatch(n ->
                            n.startsWith("io.micronaut.serde.config.annotation")
                        ))
        ));

        final List<PropertyElement> beanProperties = type.getBeanProperties();
        for (PropertyElement beanProperty : beanProperties) {
            final FieldElement f = serdeFields.get(beanProperty.getName());
            if (f != null && f.getType().equals(beanProperty.getType())) {
                replicateAnnotations(f, beanProperty);
                continue;
            }

            if (CollectionUtils.isNotEmpty(serdeMethods)) {
                final MethodElement readMethod = beanProperty.getReadMethod().orElse(null);
                final MethodElement writeMethod = beanProperty.getWriteMethod().orElse(null);
                final Iterator<MethodElement> i = serdeMethods.iterator();
                while (i.hasNext()) {
                    MethodElement serdeMethod = i.next();
                    if (readMethod != null) {
                        if (serdeMethod.getName().equals(readMethod.getName())) {
                            if (argumentsMatch(serdeMethod, readMethod)) {
                                i.remove();
                                replicateAnnotations(serdeMethod, readMethod);
                                visitMethod(readMethod, context);
                            }
                        }
                    }
                    if (writeMethod != null) {
                        if (serdeMethod.getName().equals(writeMethod.getName())) {
                            if (argumentsMatch(serdeMethod, writeMethod)) {
                                i.remove();
                                replicateAnnotations(serdeMethod, writeMethod);
                                visitMethod(writeMethod, context);
                            }
                        }
                    }
                }
            }
        }

        if (!serdeMethods.isEmpty()) {
            for (MethodElement serdeMethod : serdeMethods) {
                type.getEnclosedElement(
                        ElementQuery.ALL_METHODS
                                .onlyInstance()
                                .onlyAccessible()
                                .named(n -> n.equals(serdeMethod.getName()))
                                .filter(left -> left.getReturnType().equals(serdeMethod.getReturnType())
                                        && argumentsMatch(left, serdeMethod))
                ).ifPresent(m -> {
                    m.annotate(Executable.class);
                    replicateAnnotations(serdeMethod, m);
                    visitMethod(m, context);
                });
            }
        }
    }

    private boolean argumentsMatch(MethodElement left, MethodElement right) {
        final ParameterElement[] lp = left.getParameters();
        final ParameterElement[] rp = right.getParameters();
        if (lp.length == rp.length) {
            if (lp.length == 0) {
                return true;
            }
            for (int i = 0; i < lp.length; i++) {
                ParameterElement p1 = lp[i];
                ParameterElement p2 = rp[i];
                if (!p1.getType().equals(p2.getType())) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private void replicateAnnotations(Element source, Element target) {
        final Set<String> annotationNames = source.getAnnotationNames();
        for (String annotationName : annotationNames) {
            final AnnotationValue<?> config = source.getAnnotation(annotationName);
            if (config != null) {
                target.annotate(config);
            }
        }
    }

    @Nullable
    private PropertyNamingStrategy getPropertyNamingStrategy(TypedElement element, @Nullable PropertyNamingStrategy defaultValue) {
        String namingStrategy = element.stringValue(SerdeConfig.class, SerdeConfig.NAMING)
                .filter(val -> !val.equals(PropertyNamingStrategy.IDENTITY.getClass().getName()))
                .orElse(null);
        if (namingStrategy != null) {
            PropertyNamingStrategy propertyNamingStrategy = PropertyNamingStrategy.forName(namingStrategy).orElse(null);
            if (propertyNamingStrategy == null) {
                Object o = InstantiationUtils.tryInstantiate(
                        namingStrategy,
                        getClass().getClassLoader()
                ).orElse(null);
                if (o instanceof PropertyNamingStrategy) {
                    return (PropertyNamingStrategy) o;
                } else {
                    element.annotate(SerdeConfig.class, builder -> builder.member(SerdeConfig.RUNTIME_NAMING, namingStrategy));
                }
            }
            return propertyNamingStrategy;
        }
        return defaultValue;
    }

    private void processProperties(VisitorContext context,
                                   List<? extends TypedElement> beanProperties,
                                   List<String> orderDef,
                                   String[] ignoresProperties,
                                   String[] includeProperties,
                                   boolean ignoreOnlyDeserialization,
                                   boolean ignoreOnlySerialization,
                                   @Nullable PropertyNamingStrategy namingStrategy) {
        final Set<String> ignoredSet = CollectionUtils.setOf(ignoresProperties);
        final Set<String> includeSet = CollectionUtils.setOf(includeProperties);
        final List<String> order = new ArrayList<>(orderDef);
        for (TypedElement beanProperty : beanProperties) {
            checkForErrors(beanProperty, context);
            applyJsonbNillablePrecedence(beanProperty);

            PropertyNamingStrategy propertyNamingStrategy = getPropertyNamingStrategy(beanProperty, namingStrategy);

            if (beanProperty instanceof PropertyElement pm) {
                pm.getReadMethod().ifPresent(readMethods::add);
                pm.getWriteMethod().ifPresent(writeMethods::add);
            }
            if (!beanProperty.isPrimitive() && !beanProperty.isArray()) {
                final ClassElement t = beanProperty.getGenericType();
                handleJsonIgnoreType(context, beanProperty, t);
            }

            final String propertyName = resolvePropertyName(beanProperty);
            if (isStaticBackedProperty(beanProperty)) {
                ignoreProperty(false, false, beanProperty);
                continue;
            }
            if (propertyNamingStrategy != null) {
                beanProperty.annotate(SerdeConfig.class, (builder) ->
                    builder.member(SerdeConfig.PROPERTY, propertyNamingStrategy.translate(beanProperty))
                );
            }
            if (CollectionUtils.isNotEmpty(order)) {
                int index = order.indexOf(propertyName);
                if (index == -1) {
                    // Try to find the order defined by the original name of the property
                    index = order.indexOf(beanProperty.getName());
                }
                if (index > -1) {
                    // Set as used
                    order.set(index, "");
                    int finalIndex = index;
                    beanProperty.annotate(Order.class, (builder) ->
                        builder.value(-(finalIndex + 1))
                    );
                }
            }

            if (ignoredSet.contains(propertyName)) {
                ignoreProperty(ignoreOnlyDeserialization, ignoreOnlySerialization, beanProperty);
            } else if (!includeSet.isEmpty() && !includeSet.contains(propertyName)) {
                ignoreProperty(false, false, beanProperty);
            }
        }
    }

    private boolean isStaticBackedProperty(TypedElement beanProperty) {
        return beanProperty instanceof PropertyElement propertyElement
            && propertyElement.getField().map(FieldElement::isStatic).orElse(false);
    }

    private void applyJsonbNillablePrecedence(TypedElement beanProperty) {
        beanProperty.booleanValue(JSONB_NILLABLE)
            .filter(includeNull -> !includeNull)
            .ifPresent(includeNull -> beanProperty.annotate(SerdeConfig.class, builder ->
                builder.member(SerdeConfig.INCLUDE, SerdeConfig.SerInclude.NON_ABSENT)
            ));
    }

    private void ignoreProperty(boolean ignoreOnlyDeserialization,
                                boolean ignoreOnlySerialization,
                                TypedElement beanProperty) {
        if (beanProperty instanceof PropertyElement propertyElement) {
            if (ignoreOnlySerialization) {
                propertyElement.getReadMethod().ifPresent(e -> e.annotate(SerdeConfig.class, (builder) ->
                    builder.member(SerdeConfig.IGNORED_SERIALIZATION, true)
                ));
            } else if (ignoreOnlyDeserialization) {
                propertyElement.getWriteMethod().ifPresent(e -> e.annotate(SerdeConfig.class, (builder) ->
                    builder.member(SerdeConfig.IGNORED_DESERIALIZATION, true)
                ));
            } else {
                propertyElement.annotate(SerdeConfig.class, (builder) ->
                    builder.member(SerdeConfig.IGNORED, true)
                );
            }
        } else {
            if (ignoreOnlySerialization) {
                beanProperty.annotate(SerdeConfig.class, (builder) ->
                    builder.member(SerdeConfig.IGNORED_SERIALIZATION, true)
                );
            } else if (ignoreOnlyDeserialization) {
                beanProperty.annotate(SerdeConfig.class, (builder) ->
                    builder.member(SerdeConfig.IGNORED_DESERIALIZATION, true)
                );
            } else {
                beanProperty.annotate(SerdeConfig.class, (builder) ->
                    builder.member(SerdeConfig.IGNORED, true)
                );
            }
        }
    }

    private void handleJsonIgnoreType(VisitorContext context, TypedElement beanProperty, ClassElement t) {
        final String typeName = t.getName();
        if (!ClassUtils.isJavaBasicType(typeName)) {
            final boolean ignoredType = context.getClassElement(typeName)
                    .map((c) -> c.hasAnnotation(SerdeConfig.SerIgnored.SerType.class)).orElse(false);
            if (ignoredType) {
                beanProperty.annotate(SerdeConfig.class, (builder) ->
                        builder.member(SerdeConfig.IGNORED, true)
                );
            }
        }
    }

    private void resetForNewClass(ClassElement element) {
        this.currentClass = element;
        // NOTE: `AliasFor` does not apply here; read directly from both annotations.
        this.failOnError = element.booleanValue(SerdeConfig.class, SerdeConfig.VALIDATE)
            .or(() -> element.booleanValue(Serdeable.class, SerdeConfig.VALIDATE))
            .orElse(true);
        this.creatorMode = SerdeConfig.SerCreatorMode.PROPERTIES;
        this.anyGetterMethod = null;
        this.anySetterMethod = null;
        this.anyGetterField = null;
        this.anySetterField = null;
        this.jsonValueField = null;
        this.jsonValueMethod = null;
        this.jsonKeyField = null;
        this.jsonKeyMethod = null;
        this.readMethods.clear();
        this.writeMethods.clear();
    }

    private ClassElement currentClass() {
        return Objects.requireNonNull(currentClass);
    }

    private SerdeConfig.SerSubtyped.DiscriminatorValueKind getDiscriminatorValueKind(Element typeInfo) {
        // Missing type info might be the scenario where the JsonTypeInfo defined on the argument
        // For that case we assume the name discriminator so the name can be added to the metadata
        return typeInfo.enumValue(
                        SerdeConfig.SerSubtyped.class,
                        SerdeConfig.SerSubtyped.DISCRIMINATOR_VALUE,
                        SerdeConfig.SerSubtyped.DiscriminatorValueKind.class)
                .orElse(SerdeConfig.SerSubtyped.DiscriminatorValueKind.NAME);
    }

    private Optional<ClassElement> findTypeInfo(ClassElement element, boolean includeElement) {
        if (element.hasDeclaredAnnotation(SerdeConfig.SerSubtyped.class) && includeElement) {
            return Optional.of(element);
        }

        final ClassElement superElement = element.getSuperType().orElse(null);

        if (superElement == null) {
            ClassElement itfe = findInDeclaredInterfaces(element);
            if (itfe != null) {
                return Optional.of(itfe);
            } else {
                return Optional.empty();
            }
        }

        if (superElement.hasDeclaredAnnotation(SerdeConfig.SerSubtyped.class)) {
            return Optional.of(superElement);
        } else {
            ClassElement itfe = findInDeclaredInterfaces(element);
            if (itfe == null) {
                itfe = findInDeclaredInterfaces(superElement);
            }

            if (itfe != null) {
                return Optional.of(itfe);
            } else {
                return findTypeInfo(superElement, true);
            }
        }
    }

    private @Nullable ClassElement findInDeclaredInterfaces(ClassElement superElement) {
        Collection<ClassElement> interfaces = superElement.getInterfaces();
        if (CollectionUtils.isNotEmpty(interfaces)) {
            for (ClassElement anInterface : interfaces) {
                if (anInterface.hasDeclaredAnnotation(SerdeConfig.SerSubtyped.class)) {
                    return anInterface;
                }
                ClassElement e = findInDeclaredInterfaces(anInterface);
                if (e != null) {
                    return e;
                }
            }
        }
        return null;
    }

    private Optional<String> resolveTypeProperty(ClassElement superType) {
        ClassElement typeInfo = findTypeInfo(superType, true).orElse(null);
        if (typeInfo != null) {
            return typeInfo.stringValue(SerdeConfig.SerSubtyped.class, SerdeConfig.SerSubtyped.DISCRIMINATOR_PROP);
        }
        return Optional.empty();
    }

    private Optional<SerdeConfig.SerSubtyped.DiscriminatorType> getDiscriminatorType(Element element) {
        return element.enumValue(
                SerdeConfig.SerSubtyped.class,
                SerdeConfig.SerSubtyped.DISCRIMINATOR_TYPE,
                SerdeConfig.SerSubtyped.DiscriminatorType.class
            );
    }

    @Override
    public int getOrder() {
        return IntrospectedTypeElementVisitor.POSITION + 100;
    }

    private boolean isSerdeAnnotated(ClassElement element) {
        return Stream.of(
                        // jackson 3
                        "tools.jackson.databind.annotation.JsonNaming",
                        "tools.jackson.databind.annotation.JsonSerialize",
                        "tools.jackson.databind.annotation.JsonDeserialize",
                        // jackson 2
                        "com.fasterxml.jackson.databind.annotation.JsonNaming",
                        "com.fasterxml.jackson.databind.annotation.JsonSerialize",
                        "com.fasterxml.jackson.databind.annotation.JsonDeserialize",
                        // jackson 2 and 3
                        "com.fasterxml.jackson.annotation.JsonClassDescription",
                        "com.fasterxml.jackson.annotation.JsonTypeInfo",
                        "com.fasterxml.jackson.annotation.JsonRootName",
                        "com.fasterxml.jackson.annotation.JsonTypeName",
                        JSON_AUTO_DETECT,
                        "com.fasterxml.jackson.annotation.JsonIgnoreProperties",
                        "com.fasterxml.jackson.annotation.JsonIncludeProperties"
            ).anyMatch(element::hasDeclaredAnnotation) ||
            isJaxbAutoBindable(element) ||
            (element.hasStereotype(Serdeable.Serializable.class) || element.hasStereotype(Serdeable.Deserializable.class));
    }

    private boolean isJaxbAutoBindable(ClassElement element) {
        return !element.hasAnnotation(JAXB_XML_TRANSIENT) && Stream.of(
            JAXB_XML_ROOT_ELEMENT,
            JAXB_XML_TYPE,
            JAXB_XML_ENUM,
            JAXB_XML_ACCESSOR_ORDER,
            JAXB_XML_ACCESSOR_TYPE,
            JAXB_ANNOTATION_PREFIX + "XmlSeeAlso"
        ).anyMatch(element::hasAnnotation);
    }

    private static boolean isBasicType(ClassElement propertyType) {
        if (propertyType == null) {
            return false;
        }
        String name = propertyType.getName();
        return ClassUtils.isJavaBasicType(name) || (propertyType.isPrimitive() && !propertyType.isArray());
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }

    private record TypePropertyDescriptor(String propertyName, String propertyValue) {
    }
}
