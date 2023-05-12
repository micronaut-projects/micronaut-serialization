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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
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
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.annotation.SerdeImport;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;

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
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A visitor that provides validation and extended handling for JSON annotations.
 */
public class SerdeAnnotationVisitor implements TypeElementVisitor<SerdeConfig, SerdeConfig> {

    private boolean failOnError = true;
    private ClassElement currentClass;
    private MethodElement anyGetterMethod;
    private MethodElement anySetterMethod;
    private FieldElement anyGetterField;
    private FieldElement anySetterField;
    private MethodElement jsonValueMethod;
    private FieldElement jsonValueField;
    private final Set<String> readMethods = new HashSet<>(20);
    private final Set<String> writeMethods = new HashSet<>(20);
    private final Set<String> elementVisitedAsSubtype = new HashSet<>(10);
    private SerdeConfig.SerCreatorMode creatorMode = SerdeConfig.SerCreatorMode.PROPERTIES;

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return CollectionUtils.setOf(
                "com.fasterxml.jackson.annotation.*",
                "jakarta.json.bind.annotation.*",
                "io.micronaut.serde.annotation.*",
                "org.bson.codecs.pojo.annotations.*",
                "io.micronaut.serde.config.annotation.*",
                "com.fasterxml.jackson.databind.annotation.*"
        );
    }

    private Set<String> getUnsupportedJacksonAnnotations() {
        return CollectionUtils.setOf(
                "com.fasterxml.jackson.annotation.JsonKey",
                "com.fasterxml.jackson.annotation.JsonAutoDetect",
                "com.fasterxml.jackson.annotation.JsonMerge",
                "com.fasterxml.jackson.annotation.JsonIdentityInfo",
                "com.fasterxml.jackson.annotation.JsonIdentityReference"
        );
    }

    @Override
    public void visitField(FieldElement element, VisitorContext context) {
        if (checkForErrors(element, context)) {
            return;
        }
        checkForFieldErrors(element, context);
    }

    private void checkForFieldErrors(FieldElement element, VisitorContext context) {
        if (failOnError) {

            if (element.hasDeclaredAnnotation(SerdeConfig.SerAnyGetter.class)) {
                if (element.hasDeclaredAnnotation(SerdeConfig.SerUnwrapped.class)) {
                    context.fail("A field annotated with AnyGetter cannot be unwrapped", element);
                } else if (element.hasDeclaredAnnotation(SerdeConfig.SerValue.class)) {
                    context.fail("A field annotated with AnyGetter cannot be a JsonValue", element);
                } else if (!element.getGenericField().isAssignable(Map.class)) {
                    context.fail("A field annotated with AnyGetter must be a Map", element);
                } else {
                    if (anyGetterField != null) {
                        context.fail("Only a single AnyGetter field is supported, another defined: " + anyGetterField.getDescription(true),
                                element);
                    } else if (anyGetterMethod != null) {
                        context.fail("Cannot define both an AnyGetter field and an AnyGetter method: " + anyGetterMethod.getDescription(true),
                                element);
                    } else {
                        this.anyGetterField = element;
                    }
                }
            } else if (element.hasDeclaredAnnotation(SerdeConfig.SerAnySetter.class)) {
                if (creatorMode == SerdeConfig.SerCreatorMode.DELEGATING) {
                    context.fail("A field annotated with AnySetter cannot use DELEGATING creation", element);
                } else if (element.hasDeclaredAnnotation(SerdeConfig.SerUnwrapped.class)) {
                    context.fail("A field annotated with AnySetter cannot be unwrapped", element);
                } else if (!element.getGenericField().isAssignable(Map.class)) {
                    context.fail("A field annotated with AnySetter must be a Map", element);
                } else {
                    if (anySetterField != null) {
                        context.fail("Only a single AnySetter field is supported, another defined: " + anySetterField.getDescription(true),
                                element);
                    } else if (anySetterMethod != null) {
                        context.fail("Cannot define both an AnySetter field and an AnySetter method: " + anySetterMethod.getDescription(true),
                                element);
                    } else {
                        this.anySetterField = element;
                    }
                }
            } else if (element.hasDeclaredAnnotation(SerdeConfig.SerValue.class)) {
                if (jsonValueField != null) {
                    context.fail("A JsonValue field is already defined: " + jsonValueField, element);
                } else if (jsonValueMethod != null) {
                    context.fail("A JsonValue method is already defined: " + jsonValueMethod, element);
                } else {
                    this.jsonValueField = element;
                }
            }
        }
    }

    @Override
    public void visitConstructor(ConstructorElement element, VisitorContext context) {
        if (checkForErrors(element, context)) {
            return;
        }
    }

    @Override
    public void visitMethod(MethodElement element, VisitorContext context) {
        if (checkForErrors(element, context) || element.getDeclaringType().getAnnotationMetadata().hasAnnotation(SerdeImport.class)) {
            return;
        }
        AnnotationMetadata declaredMetadata = element.getDeclaredMetadata();
        if (declaredMetadata.hasDeclaredAnnotation(SerdeConfig.META_ANNOTATION_PROPERTY) ||
            declaredMetadata.stringValue(SerdeConfig.class, SerdeConfig.PROPERTY).isPresent()) {
            ParameterElement[] parameters = element.getParameters();
            if (element.isStatic()) {
                context.fail("A method annotated with JsonProperty cannot be static", element);
            } else if (parameters.length == 0) {
                if (element.getReturnType().getName().equals("void")) {
                    context.fail("A method annotated with JsonProperty cannot return void", element);
                } else if (!readMethods.contains(element.getName())) {
                    element.annotate(Executable.class);
                    element.annotate(SerdeConfig.SerGetter.class);
                }
            } else if (parameters.length == 1) {
                if (!writeMethods.contains(element.getName())) {

                    element.annotate(Executable.class);
                    element.annotate(SerdeConfig.SerSetter.class);
                }
            } else {
                context.fail("A method annotated with JsonProperty must specify at most 1 argument", element);
            }
        } else if (declaredMetadata.hasDeclaredAnnotation(SerdeConfig.SerGetter.class)) {
            if (element.isStatic()) {
                context.fail("A method annotated with JsonGetter cannot be static", element);
            } else if (element.getReturnType().getName().equals("void")) {
                context.fail("A method annotated with JsonGetter cannot return void", element);
            } else if (element.hasParameters()) {
                context.fail("A method annotated with JsonGetter cannot define arguments", element);
            }
        } else if (declaredMetadata.hasDeclaredAnnotation(SerdeConfig.SerSetter.class)) {
            if (element.isStatic()) {
                context.fail("A method annotated with JsonSetter cannot be static", element);
            } else {
                final ParameterElement[] parameters = element.getParameters();
                if (parameters.length != 1) {
                    context.fail("A method annotated with JsonSetter must specify exactly 1 argument", element);
                }
            }
        } else if (declaredMetadata.hasDeclaredAnnotation(SerdeConfig.SerAnyGetter.class)) {
            if (this.anyGetterMethod == null) {
                this.anyGetterMethod = element;
            } else {
                context.fail("Type already defines a method annotated with JsonAnyGetter: " + anyGetterMethod.getDescription(true), element);
            }

            if (declaredMetadata.hasDeclaredAnnotation(SerdeConfig.SerUnwrapped.class)) {
                context.fail("A method annotated with AnyGetter cannot be unwrapped", element);
            } else if (element.isStatic()) {
                context.fail("A method annotated with AnyGetter cannot be static", element);
            } else if (!element.getGenericReturnType().isAssignable(Map.class)) {
                context.fail("A method annotated with AnyGetter must return a Map", element);
            } else if (element.hasParameters()) {
                context.fail("A method annotated with AnyGetter cannot define arguments", element);
            }
        } else if (declaredMetadata.hasDeclaredAnnotation(SerdeConfig.SerAnySetter.class)) {
            if (this.anySetterMethod == null) {
                this.anySetterMethod = element;
            } else {
                context.fail("Type already defines a method annotated with JsonAnySetter: " + anySetterMethod.getDescription(true), element);
            }
            if (declaredMetadata.hasDeclaredAnnotation(SerdeConfig.SerUnwrapped.class)) {
                context.fail("A method annotated with AnyGetter cannot be unwrapped", element);
            } else if (element.isStatic()) {
                context.fail("A method annotated with AnySetter cannot be static", element);
            } else {
                final ParameterElement[] parameters = element.getParameters();
                if (parameters.length == 1) {
                   if (!parameters[0].getGenericType().isAssignable(Map.class)) {
                       context.fail("A method annotated with AnySetter must either define a single parameter of type Map or define exactly 2 parameters, the first of which should be of type String", element);
                   }
                } else if (parameters.length != 2 || !parameters[0].getGenericType().isAssignable(String.class)) {
                    context.fail("A method annotated with AnySetter must either define a single parameter of type Map or define exactly 2 parameters, the first of which should be of type String", element);
                }
            }
        } else if (declaredMetadata.hasDeclaredAnnotation(SerdeConfig.SerValue.class)) {
            if (jsonValueField != null) {
                context.fail("A JsonValue field is already defined: " + jsonValueField, element);
            } else if (jsonValueMethod != null) {
                context.fail("A JsonValue method is already defined: " + jsonValueMethod, element);
            } else {
                this.jsonValueMethod = element;
            }
        }
    }

    private boolean checkForErrors(Element element, VisitorContext context) {
        if (!failOnError) {
            return false;
        }
        if (element instanceof MethodElement) {
            if (readMethods.contains(element.getName()) && !((MethodElement) element).hasParameters()) {
                // handled by PropertyElement
                return false;
            } else if (writeMethods.contains(element.getName()) && ((MethodElement) element).getParameters().length == 1) {
                // handled by PropertyElement
                return false;
            }
        }

        if (element instanceof MethodElement && element.hasDeclaredAnnotation(SerdeConfig.class) && element.isPrivate()) {
            context.fail("JSON annotations cannot be used on private methods and constructors", element);
            return true;
        }
        for (String annotation : getUnsupportedJacksonAnnotations()) {
            if (element.hasDeclaredAnnotation(annotation)) {
                context.fail("Annotation @" + NameUtils.getSimpleName(annotation) + " is not supported", element);
                return true;
            }
        }
        final String error = element.stringValue(SerdeConfig.SerError.class).orElse(null);
        if (error != null) {
            context.fail(error, element);
            return true;
        }
        ClassElement propertyType = resolvePropertyType(element);
        if (propertyType == null) {
            return false;
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
                        context.fail("Invalid defaultValue [" + defaultValue + "] specified: " + e.getConversionError().getCause().getMessage(), element);
                        return true;
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
                    context.fail("Specified pattern [" + pattern + "] is not a valid decimal format. See the javadoc for DecimalFormat: " + e.getMessage(), element);
                    return true;
                }
            } else if (propertyType.isAssignable(Temporal.class)) {
                try {
                    DateTimeFormatter.ofPattern(pattern);
                } catch (Exception e) {
                    context.fail("Specified pattern [" + pattern + "] is not a valid date format. See the javadoc for DateTimeFormatter: " + e.getMessage(), element);
                    return true;
                }
            }
        }

        if (handleManagedRef(element, context, propertyType, isBasicType)) {
            return true;
        }
        if (handleBackRef(element, context, propertyType, isBasicType)) {
            return true;
        }

        if (hasAnnotationOnElement(element, SerdeConfig.SerUnwrapped.class)) {
            if (isBasicType(propertyType)) {
                context.fail("Unwrapped cannot be declared on basic types", element);
                return true;
            }
            final List<String> thatProperties = resolvePropertyNames(context, propertyType, element);
            final List<String> thisProperties = resolvePropertyNames(context, currentClass, null);
            for (String thisProperty : thisProperties) {
                for (String thatProperty : thatProperties) {
                    if (thisProperty.equals(thatProperty)) {
                        context.fail("Unwrapped property contains a property [" + thatProperty + "] that conflicts with an existing property of the outer type: " + currentClass.getName() + ". Consider specifying a prefix or suffix to disambiguate this conflict.", element);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private boolean handleBackRef(Element element, VisitorContext context, ClassElement propertyType, boolean isBasicType) {
        if (hasAnnotationOnElement(element, SerdeConfig.SerBackRef.class)) {
            if (hasAnnotationOnElement(element, SerdeConfig.SerUnwrapped.class)) {
                context.fail("Managed references cannot be unwrapped", element);
                return true;
            }
            if (isBasicType) {
                context.fail("Back references cannot be declared on basic types", element);
                return true;
            } else if (isCollectionType(propertyType)) {
                context.fail("Back references cannot be declared on collection, array or Map types and must be simple beans",
                             element);
                return true;
            }
            final String otherSide = element.stringValue(SerdeConfig.SerBackRef.class).orElse(null);
            final List<TypedElement> inverseElements = resolveInverseElements(
                    context,
                    propertyType,
                    SerdeConfig.SerManagedRef.class,
                    element.getName()
            );
            if (otherSide == null) {
                final int i = inverseElements.size();
                if (i > 1) {
                    context.fail("More than one potential inverse property found  " + inverseElements +
                                         ", consider specifying a value to the reference to configure the association",
                                 element);
                    return true;
                } else if (i == 0) {
                    context.fail("No inverse property found for reference of type " + propertyType.getName(), element);
                    return true;
                } else {
                    final TypedElement otherElement = inverseElements.iterator().next();
                    if (!isCompatibleInverseSide(otherElement.getGenericType(), currentClass)) {
                        context.fail("Back reference declares an incompatible inverse property [" + otherElement + "]. The "
                                             + "inverse side should be a map, collection, bean or array of the same type as the property.",
                                     element);
                        return true;
                    }
                    element.annotate(SerdeConfig.SerBackRef.class, (builder) ->
                        builder.value(otherElement.getName())
                    );
                }
            } else {
                final TypedElement otherElement = inverseElements.stream()
                        .filter(p -> p.getName().equals(otherSide))
                        .findFirst().orElse(null);
                if (otherElement == null) {
                    context.fail("Back reference declares an inverse property [" + otherSide + "] that doesn't exist in type " + propertyType.getName(),
                                 element);
                    return true;
                } else if (!isCompatibleInverseSide(otherElement.getGenericType(), currentClass)) {
                    context.fail("Back reference declares an incompatible inverse property [" + otherSide + "]. The inverse side should be a map, collection, bean or array of the same type as the property.",
                                 element);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean handleManagedRef(Element element, VisitorContext context, ClassElement propertyType, boolean isBasicType) {
        if (hasAnnotationOnElement(element, SerdeConfig.SerManagedRef.class)) {
            if (hasAnnotationOnElement(element, SerdeConfig.SerUnwrapped.class)) {
                context.fail("Managed references cannot be unwrapped", element);
                return true;
            }
            if (isBasicType) {
                context.fail("Managed references cannot be declared on basic types", element);
                return true;
            }

            final String otherSide = element.stringValue(SerdeConfig.SerManagedRef.class).orElse(null);
            if (otherSide == null) {
                final List<TypedElement> inverseElements = resolveInverseElements(
                        context,
                        resolveRefType(propertyType),
                        SerdeConfig.SerBackRef.class,
                        element.getName());

                final int i = inverseElements.size();
                if (i == 0) {
                    context.fail("No inverse property found for reference of type " + propertyType.getName(), element);
                    return true;
                } else if (i > 1) {
                    context.fail("More than one potential inverse property found " + inverseElements + ", consider specifying a value to the reference to configure the association",
                                 element);
                    return true;
                } else {
                    final TypedElement otherElement = inverseElements.iterator().next();
                    if (!isCompatibleInverseSide(otherElement.getGenericType(), currentClass)) {
                        context.fail("Managed reference declares an incompatible inverse property [" + otherElement +
                                             "]. The inverse side should be a map, collection, bean or array of the same type as the property.",
                                     element);
                        return true;
                    } else {

                        element.annotate(SerdeConfig.SerManagedRef.class, (builder) ->
                                builder.value(otherElement.getName())
                        );
                    }
                }
            }
        }
        return false;
    }

    private boolean hasAnnotationOnElement(Element element, Class<? extends Annotation> managedRefClass) {
        return element.hasDeclaredAnnotation(managedRefClass) || (
                element instanceof PropertyElement && element.hasAnnotation(managedRefClass));
    }

    private String resolveJsonName(TypedElement thisProperty) {
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
                                                      String thisSide) {
        Set<Introspected.AccessKind> accessKindSet = resolveAccessSet(context, propertyType);
        final List<TypedElement> otherElements = new ArrayList<>();
        if (accessKindSet.contains(Introspected.AccessKind.METHOD)) {
            final List<PropertyElement> beanProperties = propertyType.getBeanProperties();
            beanProperties.stream()
                    .filter(p ->
                       isMappedCandidate(refType, thisSide, p) &&
                        p.hasAnnotation(refType) &&
                        isCompatibleInverseSide(p.getGenericType(), currentClass)
                    ).forEach(otherElements::add);
        }
        if (accessKindSet.contains(Introspected.AccessKind.FIELD)) {
            final List<FieldElement> fields = propertyType
                    .getEnclosedElements(ElementQuery.ALL_FIELDS
                                                 .onlyInstance()
                                                 .annotated(ann -> isMappedCandidate(refType, thisSide, ann) && ann.hasDeclaredAnnotation(refType))
                                                 .modifiers(m -> m.contains(ElementModifier.PUBLIC))
                                                 .typed(t -> isCompatibleInverseSide(t.getGenericType(),
                                                                                     currentClass)));
            otherElements.addAll(fields);

        }
        return otherElements;
    }

    private List<String> resolvePropertyNames(
        VisitorContext context,
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
            .map(this::resolveJsonName)
            .filter(s -> (ignoreSet == null || !ignoreSet.contains(s)) &&
                (includeSet == null || includeSet.contains(s)))
            .collect(Collectors.toList());
    }

    private boolean isMappedCandidate(Class<? extends Annotation> refType, String thisSide, AnnotationMetadata p) {
        final String mappedName = p.stringValue(refType).orElse(null);
        return mappedName == null || mappedName.equals(thisSide);
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

    private boolean isCollectionType(ClassElement element) {
        return element.isArray() || element.isAssignable(Iterable.class) || element.isAssignable(Map.class);
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
        if (element instanceof FieldElement) {
            type = ((FieldElement) element).getGenericField().getType();
        } else if (element instanceof MethodElement) {
            MethodElement methodElement = (MethodElement) element;
            if (!methodElement.hasParameters()) {
                type = methodElement.getGenericReturnType();
            } else {
                type = methodElement.getParameters()[0].getGenericType();
            }
        } else if (element instanceof PropertyElement) {
            return ((PropertyElement) element).getGenericType();
        }
        return type;
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        // reset
        resetForNewClass(element);
        if (checkForErrors(element, context)) {
            return;
        }
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

    private void visitSubtype(ClassElement supertype, ClassElement subtype, VisitorContext context) {
        if (elementVisitedAsSubtype.contains(subtype.getName())) {
            return;
        }
        elementVisitedAsSubtype.add(subtype.getName());

        if (failOnError && creatorMode == SerdeConfig.SerCreatorMode.DELEGATING) {
            context.fail("Inheritance cannot be combined with DELEGATING creation", subtype);
            return;
        }

        final SerdeConfig.SerSubtyped.DiscriminatorValueKind discriminatorValueKind =
            getDiscriminatorValueKind(supertype);
        final SerdeConfig.SerSubtyped.DiscriminatorType discriminatorType =
            getDiscriminatorType(supertype);
        final String typeProperty = resolveTypeProperty(supertype).orElseGet(() ->
            discriminatorValueKind == SerdeConfig.SerSubtyped.DiscriminatorValueKind.CLASS_NAME ? "@class" : "@type"
        );

        List<String> allNames = new ArrayList<>();

        if (discriminatorValueKind == SerdeConfig.SerSubtyped.DiscriminatorValueKind.NAME) {
            subtype.stringValue(SerdeConfig.class, SerdeConfig.TYPE_NAME)
                .ifPresent(allNames::add);

            for (AnnotationValue<SerdeConfig.SerSubtyped.SerSubtype> parentSubtype:
                supertype.getDeclaredAnnotationValuesByType(SerdeConfig.SerSubtyped.SerSubtype.class)
            ) {
                String parentSubtypeName = parentSubtype.stringValue().orElse(null);
                if (subtype.getName().equals(parentSubtypeName)) {
                    parentSubtype.stringValue("name")
                        .ifPresent(allNames::add);
                    Collections.addAll(allNames, parentSubtype.stringValues("names"));
                }
            }

            if (allNames.isEmpty()) {
                // Fallback to class name
                allNames.add(subtype.getSimpleName());
            }
        } else if (discriminatorValueKind == SerdeConfig.SerSubtyped.DiscriminatorValueKind.CLASS_SIMPLE_NAME) {
            allNames.add(subtype.getSimpleName());
        } else {
            allNames.add(subtype.getName());
        }

        subtype.annotate(SerdeConfig.class, (builder) -> {
            builder.member(SerdeConfig.TYPE_NAME, allNames.get(0));
            builder.member(SerdeConfig.TYPE_NAMES, allNames.toArray(new String[0]));

            if (discriminatorType == SerdeConfig.SerSubtyped.DiscriminatorType.WRAPPER_OBJECT) {
                builder.member(SerdeConfig.WRAPPER_PROPERTY, allNames.get(0));
            } else {
                builder.member(SerdeConfig.TYPE_PROPERTY, typeProperty);
            }
        });
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
                                context.fail("Cannot mixin non-public type: " + c.getName(), element);
                            } else {
                                classValues.add(new AnnotationClassValue<>(c.getName()));
                                final ClassElement mixinType = value.stringValue("mixin").flatMap(context::getClassElement)
                                        .orElse(null);
                                if (mixinType != null) {
                                    visitMixin(mixinType, c);
                                    c.annotate(value);
                                } else {
                                    visitClassInternal(c, context, true);
                                    c.annotate(value);
                                }
                            }
                        });
            }
            element.annotate(Introspected.class, (builder) ->
                builder.member("classes", classValues.toArray(new AnnotationClassValue[0]))
            );
        } else if (isJsonAnnotated(element) || isImport) {
            if (!element.hasStereotype(Serdeable.Serializable.class) &&
                    !element.hasStereotype(Serdeable.Deserializable.class) && !isImport) {
                element.annotate(Serdeable.class);
                element.annotate(Introspected.class, (builder) -> {
                    builder.member("accessKind", Introspected.AccessKind.METHOD, Introspected.AccessKind.FIELD);
                    builder.member("visibility", "PUBLIC");
                });
            }

            String serializeAs = element.getDeclaredMetadata().stringValue(SerdeConfig.class, SerdeConfig.SERIALIZE_AS).orElse(null);
            if (serializeAs != null) {
                ClassElement thatType = context.getClassElement(serializeAs).orElse(null);
                if (thatType != null && !thatType.isAssignable(element)) {
                    context.fail("Type to serialize as [" + serializeAs + "], must be a subtype of the annotated type: " + element.getName(),
                                 element);
                    return;
                }
            }

            String deserializeAs = element.getDeclaredMetadata().stringValue(SerdeConfig.class, SerdeConfig.DESERIALIZE_AS).orElse(null);
            if (deserializeAs != null) {
                ClassElement thatType = context.getClassElement(deserializeAs).orElse(null);
                if (thatType != null && !thatType.isAssignable(element)) {
                    context.fail("Type to deserialize as [" + deserializeAs + "], must be a subtype of the annotated type: " + element.getName(),
                                 element);
                    return;
                }
            }

            final MethodElement primaryConstructor = element.getPrimaryConstructor().orElse(null);
            if (primaryConstructor != null) {

                this.creatorMode = primaryConstructor.enumValue(Creator.class, "mode", SerdeConfig.SerCreatorMode.class).orElse(null);
                if (creatorMode == SerdeConfig.SerCreatorMode.DELEGATING) {
                    if (failOnError && primaryConstructor.getParameters().length != 1) {
                        context.fail("DELEGATING creator mode requires exactly one Creator parameter, but more were defined.",
                                     element);
                    }
                }
            }

            final List<PropertyElement> beanProperties = element.getBeanProperties();
            final List<String> order = Arrays.asList(element.stringValues(SerdeConfig.META_ANNOTATION_PROPERTY_ORDER));
            Collections.reverse(order);
            final Set<Introspected.AccessKind> access = CollectionUtils.setOf(element.enumValues(Introspected.class,
                                                                                                 "accessKind",
                                                                                                 Introspected.AccessKind.class));
            boolean supportFields = access.contains(Introspected.AccessKind.FIELD);
            final String[] ignoresProperties = element.stringValues(SerdeConfig.SerIgnored.class);
            final String[] includeProperties = element.stringValues(SerdeConfig.SerIncluded.class);

            final boolean allowGetters = element.booleanValue(SerdeConfig.SerIgnored.class, "allowGetters").orElse(false);
            final boolean allowSetters = element.booleanValue(SerdeConfig.SerIgnored.class, "allowSetters").orElse(false);
            PropertyNamingStrategy propertyNamingStrategy = getPropertyNamingStrategy(element, null);
            processProperties(
                    context,
                    beanProperties,
                    order,
                    ignoresProperties,
                    includeProperties,
                    allowGetters,
                    allowSetters,
                    propertyNamingStrategy
            );
            if (supportFields) {
                final List<FieldElement> fields = element.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyInstance()
                                                                                                .onlyAccessible());
                processProperties(
                        context,
                        fields,
                        order,
                        ignoresProperties,
                        includeProperties,
                        allowGetters,
                        allowSetters,
                        propertyNamingStrategy
                );
            }

            findTypeInfo(element, false)
                .ifPresent(superType -> visitSubtype(superType, element, context));

            if (failOnError && element.hasDeclaredAnnotation(SerdeConfig.SerSubtyped.class) && creatorMode == SerdeConfig.SerCreatorMode.DELEGATING) {
                context.fail("Inheritance cannot be combined with DELEGATING creation", element);
            }
        }
    }

    private void visitMixin(ClassElement mixinType, ClassElement type) {
        mixinType.getAnnotationNames()
                .stream().filter(n -> n.startsWith("io.micronaut.serde"))
                .forEach(n -> {
                    final AnnotationValue<Annotation> ann = mixinType.getAnnotation(n);
                    if (ann != null) {
                        type.annotate(ann);
                    }
                });
        mixinType.findAnnotation(SerdeConfig.class).ifPresent(type::annotate);
        final Map<String, FieldElement> serdeFields = mixinType.getEnclosedElements(
                ElementQuery.ALL_FIELDS
                        .onlyInstance()
                        .onlyDeclared()
                        .annotated((ann) -> ann.hasAnnotation(SerdeConfig.class))
        ).stream().collect(Collectors.toMap(
                FieldElement::getName,
                (e) -> e
        ));

        final MethodElement mixinCtor = mixinType.getPrimaryConstructor().orElse(null);
        final MethodElement targetCtor = type.getPrimaryConstructor().orElse(null);
        if (mixinCtor != null && targetCtor != null && argumentsMatch(mixinCtor, targetCtor)) {
            replicateAnnotations(mixinCtor, targetCtor);
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
                                replicateAnnotations(serdeMethod, beanProperty);
                                replicateAnnotations(serdeMethod, readMethod);
                            }
                        }
                    }
                    if (writeMethod != null) {
                        if (serdeMethod.getName().equals(writeMethod.getName())) {
                            if (argumentsMatch(serdeMethod, writeMethod)) {
                                i.remove();
                                replicateAnnotations(serdeMethod, beanProperty);
                                replicateAnnotations(serdeMethod, writeMethod);
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
    private PropertyNamingStrategy getPropertyNamingStrategy(@NonNull  TypedElement element, @Nullable PropertyNamingStrategy defaultValue) {
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
                                   List<String> order,
                                   String[] ignoresProperties,
                                   String[] includeProperties,
                                   boolean allowGetters,
                                   boolean allowSetters,
                                   @Nullable PropertyNamingStrategy namingStrategy) {
        final Set<String> ignoredSet = CollectionUtils.setOf(ignoresProperties);
        final Set<String> includeSet = CollectionUtils.setOf(includeProperties);
        for (TypedElement beanProperty : beanProperties) {
            if (checkForErrors(beanProperty, context)) {
                continue;
            }
            PropertyNamingStrategy propertyNamingStrategy = getPropertyNamingStrategy(beanProperty, namingStrategy);

            if (beanProperty instanceof PropertyElement) {
                PropertyElement pm = (PropertyElement) beanProperty;
                pm.getReadMethod().ifPresent(rm ->
                    readMethods.add(rm.getName())
                );
                pm.getWriteMethod().ifPresent(rm ->
                    writeMethods.add(rm.getName())
                );
            }
            if (!beanProperty.isPrimitive() && !beanProperty.isArray()) {
                final ClassElement t = beanProperty.getGenericType();
                handleJsonIgnoreType(context, beanProperty, t);
            }

            final String propertyName = beanProperty.getName();
            if (propertyNamingStrategy != null) {
                beanProperty.annotate(SerdeConfig.class, (builder) ->
                    builder.member(SerdeConfig.PROPERTY, propertyNamingStrategy.translate(beanProperty))
                );
            }
            if (CollectionUtils.isNotEmpty(order)) {
                final int i = order.indexOf(propertyName);
                if (i > -1) {
                    beanProperty.annotate(Order.class, (builder) ->
                        builder.value(-(i + 1))
                    );
                }
            }

            if (ignoredSet.contains(propertyName)) {
                ignoreProperty(allowGetters, allowSetters, beanProperty);
            } else if (!includeSet.isEmpty() && !includeSet.contains(propertyName)) {
                ignoreProperty(allowGetters, allowSetters, beanProperty);
            }
        }
    }

    private void ignoreProperty(boolean allowGetters, boolean allowSetters, TypedElement beanProperty) {
        final Consumer<Element> configurer = m ->
                m.annotate(SerdeConfig.class, (builder) ->
                        builder.member(SerdeConfig.IGNORED, true)
                );
        if (beanProperty instanceof PropertyElement) {
            final PropertyElement propertyElement = (PropertyElement) beanProperty;
            if (allowGetters) {
                propertyElement.getWriteMethod().ifPresent(configurer);
            } else if (allowSetters) {
                propertyElement.getReadMethod().ifPresent(configurer);
            } else {
                configurer.accept(beanProperty);
            }
        } else {
            configurer.accept(beanProperty);
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
        this.failOnError = element.booleanValue(SerdeConfig.class, "validate").orElse(true);
        this.creatorMode = SerdeConfig.SerCreatorMode.PROPERTIES;
        this.anyGetterMethod = null;
        this.anySetterMethod = null;
        this.anyGetterField = null;
        this.anySetterField = null;
        this.jsonValueField = null;
        this.jsonValueMethod = null;
        this.readMethods.clear();
        this.writeMethods.clear();
    }

    private SerdeConfig.SerSubtyped.DiscriminatorValueKind getDiscriminatorValueKind(ClassElement typeInfo) {
        return typeInfo.enumValue(
                        SerdeConfig.SerSubtyped.class,
                        SerdeConfig.SerSubtyped.DISCRIMINATOR_VALUE,
                        SerdeConfig.SerSubtyped.DiscriminatorValueKind.class)
                .orElse(SerdeConfig.SerSubtyped.DiscriminatorValueKind.CLASS_NAME);
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

    private ClassElement findInDeclaredInterfaces(@NonNull ClassElement superElement) {
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

    private Optional<String> resolveTypeProperty(@NonNull ClassElement superType) {
        ClassElement typeInfo = findTypeInfo(superType, true).orElse(null);
        if (typeInfo != null) {
            return typeInfo.stringValue(SerdeConfig.SerSubtyped.class, SerdeConfig.SerSubtyped.DISCRIMINATOR_PROP);
        }
        return Optional.empty();
    }

    private SerdeConfig.SerSubtyped.DiscriminatorType getDiscriminatorType(ClassElement element) {
        return element.enumValue(
                SerdeConfig.SerSubtyped.class,
                SerdeConfig.SerSubtyped.DISCRIMINATOR_TYPE,
                SerdeConfig.SerSubtyped.DiscriminatorType.class
            ).orElse(SerdeConfig.SerSubtyped.DiscriminatorType.PROPERTY);
    }

    @Override
    public int getOrder() {
        return IntrospectedTypeElementVisitor.POSITION + 100;
    }

    private boolean isJsonAnnotated(ClassElement element) {
        return Stream.of(
                        "com.fasterxml.jackson.annotation.JsonClassDescription",
                        "com.fasterxml.jackson.databind.annotation.JsonNaming",
                        "com.fasterxml.jackson.databind.annotation.JsonSerialize",
                        "com.fasterxml.jackson.databind.annotation.JsonDeserialize",
                        "com.fasterxml.jackson.annotation.JsonTypeInfo",
                        "com.fasterxml.jackson.annotation.JsonRootName",
                        "com.fasterxml.jackson.annotation.JsonTypeName",
                        "com.fasterxml.jackson.annotation.JsonTypeId",
                        "com.fasterxml.jackson.annotation.JsonAutoDetect")
                .anyMatch(element::hasDeclaredAnnotation) ||
                (element.hasStereotype(Serdeable.Serializable.class) || element.hasStereotype(Serdeable.Deserializable.class));
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
}
