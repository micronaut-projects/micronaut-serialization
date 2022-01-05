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
import io.micronaut.core.annotation.AnnotationValueBuilder;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
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
    private SerdeConfig.CreatorMode creatorMode = SerdeConfig.CreatorMode.PROPERTIES;

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
                "com.fasterxml.jackson.annotation.JsonFilter",
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

            if (element.hasDeclaredAnnotation(SerdeConfig.AnyGetter.class)) {
                if (element.hasDeclaredAnnotation(SerdeConfig.Unwrapped.class)) {
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
            } else if (element.hasDeclaredAnnotation(SerdeConfig.AnySetter.class)) {
                if (creatorMode == SerdeConfig.CreatorMode.DELEGATING) {
                    context.fail("A field annotated with AnySetter cannot use DELEGATING creation", element);
                } else if (element.hasDeclaredAnnotation(SerdeConfig.Unwrapped.class)) {
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
        if (checkForErrors(element, context)) {
            return;
        }
        AnnotationMetadata declaredMetadata = element.getDeclaredMetadata();
        if (declaredMetadata.hasDeclaredAnnotation(SerdeConfig.Property.class) ||
            declaredMetadata.stringValue(SerdeConfig.class, SerdeConfig.PROPERTY).isPresent()) {
            ParameterElement[] parameters = element.getParameters();
            if (element.isStatic()) {
                context.fail("A method annotated with JsonProperty cannot be static", element);
            } else if (parameters.length == 0) {
                if (element.getReturnType().getName().equals("void")) {
                    context.fail("A method annotated with JsonProperty cannot return void", element);
                } else if (!readMethods.contains(element.getName())) {
                    element.annotate(Executable.class);
                    element.annotate(SerdeConfig.Getter.class);
                }
            } else if (parameters.length == 1) {
                if (!writeMethods.contains(element.getName())) {

                    element.annotate(Executable.class);
                    element.annotate(SerdeConfig.Setter.class);
                }
            } else {
                context.fail("A method annotated with JsonProperty must specify at most 1 argument", element);
            }
        } else if (declaredMetadata.hasDeclaredAnnotation(SerdeConfig.Getter.class)) {
            if (element.isStatic()) {
                context.fail("A method annotated with JsonGetter cannot be static", element);
            } else if (element.getReturnType().getName().equals("void")) {
                context.fail("A method annotated with JsonGetter cannot return void", element);
            } else if (element.hasParameters()) {
                context.fail("A method annotated with JsonGetter cannot define arguments", element);
            }
        } else if (declaredMetadata.hasDeclaredAnnotation(SerdeConfig.Setter.class)) {
            if (element.isStatic()) {
                context.fail("A method annotated with JsonSetter cannot be static", element);
            } else {
                final ParameterElement[] parameters = element.getParameters();
                if (parameters.length != 1) {
                    context.fail("A method annotated with JsonSetter must specify exactly 1 argument", element);
                }
            }
        } else if (declaredMetadata.hasDeclaredAnnotation(SerdeConfig.AnyGetter.class)) {
            if (this.anyGetterMethod == null) {
                this.anyGetterMethod = element;
            } else {
                context.fail("Type already defines a method annotated with JsonAnyGetter: " + anyGetterMethod.getDescription(true), element);
            }

            if (declaredMetadata.hasDeclaredAnnotation(SerdeConfig.Unwrapped.class)) {
                context.fail("A method annotated with AnyGetter cannot be unwrapped", element);
            } else if (element.isStatic()) {
                context.fail("A method annotated with AnyGetter cannot be static", element);
            } else if (!element.getGenericReturnType().isAssignable(Map.class)) {
                context.fail("A method annotated with AnyGetter must return a Map", element);
            } else if (element.hasParameters()) {
                context.fail("A method annotated with AnyGetter cannot define arguments", element);
            }
        } else if (declaredMetadata.hasDeclaredAnnotation(SerdeConfig.AnySetter.class)) {
            if (this.anySetterMethod == null) {
                this.anySetterMethod = element;
            } else {
                context.fail("Type already defines a method annotated with JsonAnySetter: " + anySetterMethod.getDescription(true), element);
            }
            if (declaredMetadata.hasDeclaredAnnotation(SerdeConfig.Unwrapped.class)) {
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
        final String error = element.stringValue(SerdeConfig.SerdeError.class).orElse(null);
        if (error != null) {
            context.fail(error, element);
            return true;
        }
        ClassElement propertyType = resolvePropertyType(element);
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

        if (element.hasDeclaredAnnotation(SerdeConfig.ManagedRef.class)) {
            if (element.hasDeclaredAnnotation(SerdeConfig.Unwrapped.class)) {
                context.fail("Managed references cannot be unwrapped", element);
                return true;
            }
            if (isBasicType) {
                context.fail("Managed references cannot be declared on basic types", element);
                return true;
            }

            final String otherSide = element.stringValue(SerdeConfig.ManagedRef.class).orElse(null);
            if (otherSide == null) {
                final List<TypedElement> inverseElements = resolveInverseElements(
                        context,
                        resolveRefType(propertyType),
                        SerdeConfig.BackRef.class,
                        element.getName());

                final int i = inverseElements.size();
                if (i == 0) {
                    context.fail("No inverse property found for reference of type " + propertyType.getName(), element);
                    return true;
                } else if (i > 1) {
                    context.fail("More than one potential inverse property found " + inverseElements + ", consider specifying a value to the reference to configure the association", element);
                    return true;
                } else {
                    final TypedElement otherElement = inverseElements.iterator().next();
                    if (!isCompatibleInverseSide(otherElement.getGenericType(), currentClass)) {
                        context.fail("Managed reference declares an incompatible inverse property [" + otherElement + "]. The inverse side should be a map, collection, bean or array of the same type as the property.", element);
                        return true;
                    } else {

                        element.annotate(SerdeConfig.ManagedRef.class, (builder) ->
                                builder.value(otherElement.getName())
                        );
                    }
                }
            }
        }
        if (element.hasDeclaredAnnotation(SerdeConfig.BackRef.class)) {
            if (element.hasDeclaredAnnotation(SerdeConfig.Unwrapped.class)) {
                context.fail("Managed references cannot be unwrapped", element);
                return true;
            }
            if (isBasicType) {
                context.fail("Back references cannot be declared on basic types", element);
                return true;
            } else if (isCollectionType(propertyType)) {
                context.fail("Back references cannot be declared on collection, array or Map types and must be simple beans", element);
                return true;
            }
            final String otherSide = element.stringValue(SerdeConfig.BackRef.class).orElse(null);
            final List<TypedElement> inverseElements = resolveInverseElements(
                    context,
                    propertyType,
                    SerdeConfig.ManagedRef.class,
                    element.getName()
            );
            if (otherSide == null) {
                final int i = inverseElements.size();
                if (i > 1) {
                    context.fail("More than one potential inverse property found  " + inverseElements + ", consider specifying a value to the reference to configure the association", element);
                    return true;
                } else if (i == 0) {
                    context.fail("No inverse property found for reference of type " + propertyType.getName(), element);
                    return true;
                } else {
                    final TypedElement otherElement = inverseElements.iterator().next();
                    if (!isCompatibleInverseSide(otherElement.getGenericType(), currentClass)) {
                        context.fail("Back reference declares an incompatible inverse property [" + otherElement + "]. The inverse side should be a map, collection, bean or array of the same type as the property.", element);
                        return true;
                    }
                    element.annotate(SerdeConfig.BackRef.class, (builder) ->
                        builder.value(otherElement.getName())
                    );
                }
            } else {
                final TypedElement otherElement = inverseElements.stream()
                        .filter(p -> p.getName().equals(otherSide))
                        .findFirst().orElse(null);
                if (otherElement == null) {
                    context.fail("Back reference declares an inverse property [" + otherSide + "] that doesn't exist in type " + propertyType.getName(), element);
                    return true;
                } else if (!isCompatibleInverseSide(otherElement.getGenericType(), currentClass)) {
                    context.fail("Back reference declares an incompatible inverse property [" + otherSide + "]. The inverse side should be a map, collection, bean or array of the same type as the property.", element);
                    return true;
                }
            }
        }

        if (element.hasDeclaredAnnotation(SerdeConfig.Unwrapped.class)) {
            if (isBasicType(propertyType)) {
                context.fail("Unwrapped cannot be declared on basic types", element);
                return true;
            }
            final List<TypedElement> thatProperties = resolveProperties(context, propertyType);
            final List<TypedElement> thisProperties = resolveProperties(context, currentClass);
            for (TypedElement thisProperty : thisProperties) {
                String thisName = resolveJsonName(thisProperty);
                for (TypedElement thatProperty : thatProperties) {
                    String thatName = resolveJsonName(thatProperty);
                    if (thisName.equals(thatName)) {
                        context.fail("Unwrapped property contains a property [" + thatName + "] that conflicts with an existing property of the outer type: " + currentClass.getName() + ". Consider specifying a prefix or suffix to disambiguate this conflict.", element);
                        return true;
                    }
                }
            }
        }
        return false;
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
            propertyType.getBeanProperties().stream()
                    .filter(p ->
                       isMappedCandidate(refType, thisSide, p) &&
                        p.hasDeclaredAnnotation(refType) &&
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

    private List<TypedElement> resolveProperties(VisitorContext context,
                                                  ClassElement propertyType) {
        Set<Introspected.AccessKind> accessKindSet = resolveAccessSet(context, propertyType);
        final List<TypedElement> otherElements = new ArrayList<>();
        if (accessKindSet.contains(Introspected.AccessKind.METHOD)) {
            propertyType.getBeanProperties().stream()
                    .filter(p -> !p.hasDeclaredAnnotation(SerdeConfig.Ignored.class)).forEach(otherElements::add);
        }
        if (accessKindSet.contains(Introspected.AccessKind.FIELD)) {
            final List<FieldElement> fields = propertyType
                    .getEnclosedElements(ElementQuery.ALL_FIELDS
                                                 .onlyInstance()
                                                 .annotated(ann -> !ann.hasDeclaredAnnotation(SerdeConfig.Ignored.class))
                                                 .modifiers(m -> m.contains(ElementModifier.PUBLIC)));
            otherElements.addAll(fields);

        }
        return otherElements;
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

    private ClassElement resolvePropertyType(Element element) {
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

        List<AnnotationValue<SerdeConfig.Subtyped.Subtype>> subtypes = element.getDeclaredAnnotationValuesByType(SerdeConfig.Subtyped.Subtype.class);
        if (!subtypes.isEmpty()) {
            final SerdeConfig.Subtyped.DiscriminatorValueKind discriminatorValueKind = getDiscriminatorValueKind(element);
            String typeProperty = resolveTypeProperty(element).orElseGet(() ->
                    discriminatorValueKind == SerdeConfig.Subtyped.DiscriminatorValueKind.CLASS_NAME ? "@class" : "@type"
            );
            for (AnnotationValue<SerdeConfig.Subtyped.Subtype> subtype : subtypes) {
                String className = subtype.stringValue().orElse(null);
                if (className != null) {
                    ClassElement subElement = context.getClassElement(className).orElse(null);
                    String[] names = subtype.stringValues("names");
                    String typeName;

                    if (subElement != null && !subElement.hasStereotype(SerdeConfig.class)) {
                        if (ArrayUtils.isNotEmpty(names)) {
                            typeName = names[0]; // TODO: support multiple
                        } else {
                            typeName = subElement.getSimpleName();
                        }
                        subElement.annotate(Serdeable.class);
                        subElement.annotate(SerdeConfig.class, (builder) -> {
                            String include = resolveInclude(element).orElse(null);
                            handleSubtypeInclude(builder, typeName, typeProperty, include);
                        });
                    }
                }
            }
        }
        if (element.hasAnnotation(SerdeImport.Repeated.class)) {
            final List<AnnotationValue<SerdeImport>> values = element.getAnnotationValuesByType(SerdeImport.class);
            List<AnnotationClassValue<?>> classValues = new ArrayList<>();
            for (AnnotationValue<SerdeImport> value : values) {
                value.annotationClassValue(AnnotationMetadata.VALUE_MEMBER)
                        .flatMap(acv -> context.getClassElement(acv.getName()))
                        .ifPresent(c -> {
                            if (!c.isPublic()) {
                                context.fail("Cannot mixin non-public type: " + c.getName(), element);
                            } else {
                                classValues.add(new AnnotationClassValue<>(c.getName()));
                                visitClass(c, context);
                            }
                        });
            }
            element.annotate(Introspected.class, (builder) ->
                builder.member("classes", classValues.toArray(new AnnotationClassValue[0]))
            );
        } else if (isJsonAnnotated(element)) {
            if (!element.hasStereotype(Serdeable.Serializable.class) &&
                    !element.hasStereotype(Serdeable.Deserializable.class)) {
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
                    context.fail("Type to serialize as [" + serializeAs + "], must be a subtype of the annotated type: " + element.getName(), element);
                    return;
                }
            }

            String deserializeAs = element.getDeclaredMetadata().stringValue(SerdeConfig.class, SerdeConfig.DESERIALIZE_AS).orElse(null);
            if (deserializeAs != null) {
                ClassElement thatType = context.getClassElement(deserializeAs).orElse(null);
                if (thatType != null && !thatType.isAssignable(element)) {
                    context.fail("Type to deserialize as [" + deserializeAs + "], must be a subtype of the annotated type: " + element.getName(), element);
                    return;
                }
            }

            final MethodElement primaryConstructor = element.getPrimaryConstructor().orElse(null);
            if (primaryConstructor != null) {

                this.creatorMode = primaryConstructor.enumValue(Creator.class, "mode", SerdeConfig.CreatorMode.class).orElse(null);
                if (creatorMode == SerdeConfig.CreatorMode.DELEGATING) {
                    if (failOnError && primaryConstructor.getParameters().length != 1) {
                        context.fail("DELEGATING creator mode requires exactly one Creator parameter, but more were defined.", element);
                    }
                }
            }

            final List<PropertyElement> beanProperties = element.getBeanProperties();
            final List<String> order = Arrays.asList(element.stringValues(SerdeConfig.PropertyOrder.class));
            Collections.reverse(order);
            final Set<Introspected.AccessKind> access = CollectionUtils.setOf(element.enumValues(Introspected.class,
                                                                                                     "accessKind",
                                                                                                     Introspected.AccessKind.class));
            boolean supportFields = access.contains(Introspected.AccessKind.FIELD);
            final String[] ignoresProperties = element.stringValues(SerdeConfig.Ignored.class);
            final String[] includeProperties = element.stringValues(SerdeConfig.Included.class);

            final boolean allowGetters = element.booleanValue(SerdeConfig.Ignored.class, "allowGetters").orElse(false);
            final boolean allowSetters = element.booleanValue(SerdeConfig.Ignored.class, "allowSetters").orElse(false);
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

            final Optional<ClassElement> superType = findTypeInfo(element, false);
            if (superType.isPresent()) {
                final ClassElement typeInfo = superType.get();
                if (failOnError && creatorMode == SerdeConfig.CreatorMode.DELEGATING) {
                    context.fail("Inheritance cannot be combined with DELEGATING creation", element);
                    return;
                }
                final SerdeConfig.Subtyped.DiscriminatorValueKind discriminatorValueKind = getDiscriminatorValueKind(typeInfo);
                element.annotate(SerdeConfig.class, builder -> {
                    final String typeName = element.stringValue(SerdeConfig.class, SerdeConfig.TYPE_NAME).orElseGet(() ->
                          discriminatorValueKind == SerdeConfig.Subtyped.DiscriminatorValueKind.CLASS_NAME ? element.getName() : element.getSimpleName()
                    );
                    String typeProperty = resolveTypeProperty(typeInfo).orElseGet(() ->
                       discriminatorValueKind == SerdeConfig.Subtyped.DiscriminatorValueKind.CLASS_NAME ? "@class" : "@type"
                    );
                    final String include = resolveInclude(typeInfo).orElse(null);
                    handleSubtypeInclude(builder, typeName, typeProperty, include);
                });
            }

            if (failOnError && element.hasDeclaredAnnotation(SerdeConfig.Subtyped.class) && creatorMode == SerdeConfig.CreatorMode.DELEGATING) {
                context.fail("Inheritance cannot be combined with DELEGATING creation", element);
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

    private void handleSubtypeInclude(AnnotationValueBuilder<SerdeConfig> builder, String typeName, String typeProperty, String include) {
        if ("WRAPPER_OBJECT".equalsIgnoreCase(include)) {
            builder.member(SerdeConfig.TYPE_NAME, typeName);
            builder.member(SerdeConfig.WRAPPER_PROPERTY, typeName);
        } else if (typeProperty != null) {
            builder.member(SerdeConfig.TYPE_NAME, typeName);
            builder.member(SerdeConfig.TYPE_PROPERTY, typeProperty);
        }
    }

    private void processProperties(VisitorContext context,
                                   List<? extends TypedElement> beanProperties,
                                   List<String> order,
                                   String[] ignoresProperties,
                                   String[] includeProperties,
                                   boolean allowGetters,
                                   boolean allowSetters,
                                   @Nullable PropertyNamingStrategy _namingStrategy) {
        final Set<String> ignoredSet = CollectionUtils.setOf(ignoresProperties);
        final Set<String> includeSet = CollectionUtils.setOf(includeProperties);
        for (TypedElement beanProperty : beanProperties) {
            PropertyNamingStrategy propertyNamingStrategy = getPropertyNamingStrategy(beanProperty, _namingStrategy);

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
                    .map((c) -> c.hasAnnotation(SerdeConfig.Ignored.Type.class)).orElse(false);
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
        this.creatorMode = SerdeConfig.CreatorMode.PROPERTIES;
        this.anyGetterMethod = null;
        this.anySetterMethod = null;
        this.anyGetterField = null;
        this.anySetterField = null;
        this.jsonValueField = null;
        this.jsonValueMethod = null;
        this.readMethods.clear();
        this.writeMethods.clear();
    }

    private SerdeConfig.Subtyped.DiscriminatorValueKind getDiscriminatorValueKind(ClassElement typeInfo) {
        return typeInfo.enumValue(
                SerdeConfig.Subtyped.class,
                SerdeConfig.Subtyped.DISCRIMINATOR_VALUE,
                SerdeConfig.Subtyped.DiscriminatorValueKind.class)
                .orElse(SerdeConfig.Subtyped.DiscriminatorValueKind.CLASS_NAME);
    }

    private Optional<ClassElement> findTypeInfo(ClassElement element, boolean includeElement) {
        // TODO: support interfaces
        if (element.hasDeclaredAnnotation(SerdeConfig.Subtyped.class) && includeElement) {
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

        if (superElement.hasDeclaredAnnotation(SerdeConfig.Subtyped.class)) {
            return Optional.of(superElement);
        } else {
            ClassElement itfe = findInDeclaredInterfaces(superElement);
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
                if (anInterface.hasDeclaredAnnotation(SerdeConfig.Subtyped.class)) {
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
            return typeInfo.stringValue(SerdeConfig.Subtyped.class, SerdeConfig.Subtyped.DISCRIMINATOR_PROP);
        }
        return Optional.empty();
    }

    private Optional<String> resolveInclude(ClassElement superType) {
        ClassElement typeInfo = findTypeInfo(superType, true).orElse(null);
        if (typeInfo != null) {
            return typeInfo.stringValue(SerdeConfig.Subtyped.class, SerdeConfig.Subtyped.DISCRIMINATOR_TYPE);
        }
        return Optional.empty();
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
