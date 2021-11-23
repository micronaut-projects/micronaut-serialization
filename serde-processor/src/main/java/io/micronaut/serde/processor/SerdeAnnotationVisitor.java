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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIdentityReference;
import com.fasterxml.jackson.annotation.JsonKey;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonMerge;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonTypeId;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.micronaut.context.annotation.DefaultImplementation;
import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.*;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.TypedElement;
import io.micronaut.inject.beans.visitor.IntrospectedTypeElementVisitor;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.annotation.SerdeConfig;
import io.micronaut.serde.annotation.SerdeMixin;
import io.micronaut.serde.annotation.Serdeable;

import java.lang.annotation.Annotation;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * A visitor that provides validation and extended handling for JSON annotations.
 */
public class SerdeAnnotationVisitor implements TypeElementVisitor<SerdeConfig, SerdeConfig> {

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
                "org.bson.codecs.pojo.annotations.*"
        );
    }

    private Set<Class<? extends Annotation>> getUnsupportedJacksonAnnotations() {
        return CollectionUtils.setOf(
                JsonKey.class,
                JsonFilter.class,
                JsonBackReference.class,
                JsonAutoDetect.class,
                JsonMerge.class,
                JsonIdentityInfo.class,
                JsonIdentityReference.class,
                JsonManagedReference.class
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
        for (Class<? extends Annotation> annotation : getUnsupportedJacksonAnnotations()) {
            if (element.hasDeclaredAnnotation(annotation)) {
                context.fail("Annotation @" + annotation.getSimpleName() + " is not supported", element);
            }
        }
        final String error = element.stringValue(SerdeConfig.SerdeError.class).orElse(null);
        if (error != null) {
            context.fail(error, element);
            return true;
        }

        final String pattern = element.stringValue(SerdeConfig.class, SerdeConfig.PATTERN).orElse(null);
        if (pattern != null) {
            ClassElement type = resolvePropertyType(element);

            if (isNumberType(type)) {
                try {
                    new DecimalFormat(pattern);
                } catch (Exception e) {
                    context.fail("Specified pattern [" + pattern + "] is not a valid decimal format. See the javadoc for DecimalFormat: " + e.getMessage(), element);
                }
            } else if (type.isAssignable(Temporal.class)) {
                try {
                    DateTimeFormatter.ofPattern(pattern);
                } catch (Exception e) {
                    context.fail("Specified pattern [" + pattern + "] is not a valid date format. See the javadoc for DateTimeFormatter: " + e.getMessage(), element);
                }
            }
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
        resetForNewClass();
        if (checkForErrors(element, context)) {
            return;
        }
        List<AnnotationValue<SerdeConfig.Subtyped.Subtype>> subtypes = element.getDeclaredAnnotationValuesByType(SerdeConfig.Subtyped.Subtype.class);
        if (!subtypes.isEmpty()) {
            final SerdeConfig.Subtyped.DiscriminatorValueKind discriminatorValueKind = getDiscriminatorValueKind(element);
            String typeProperty = resolveTypeProperty(Optional.of(element)).orElseGet(() ->
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
                            JsonTypeInfo.As include = resolveInclude(Optional.of(element)).orElse(null);
                            handleSubtypeInclude(builder, typeName, typeProperty, include);
                        });
                    }
                }
            }
        }
        if (element.hasAnnotation(SerdeMixin.Repeated.class)) {
            final List<AnnotationValue<SerdeMixin>> values = element.getAnnotationValuesByType(SerdeMixin.class);
            List<AnnotationClassValue<?>> classValues = new ArrayList<>();
            for (AnnotationValue<SerdeMixin> value : values) {
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

            final MethodElement primaryConstructor = element.getPrimaryConstructor().orElse(null);
            if (primaryConstructor != null) {

                this.creatorMode = primaryConstructor.enumValue(Creator.class, "mode", SerdeConfig.CreatorMode.class).orElse(null);
                if (creatorMode == SerdeConfig.CreatorMode.DELEGATING) {
                    if (primaryConstructor.getParameters().length != 1) {
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
            processProperties(
                    context,
                    beanProperties,
                    order,
                    ignoresProperties,
                    includeProperties,
                    allowGetters,
                    allowSetters
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
                        allowSetters
                );
            }

            final Optional<ClassElement> superType = findTypeInfo(element);
            if (superType.isPresent()) {
                final ClassElement typeInfo = superType.get();
                if (creatorMode == SerdeConfig.CreatorMode.DELEGATING) {
                    context.fail("Inheritance cannot be combined with DELEGATING creation", element);
                    return;
                }
                final SerdeConfig.Subtyped.DiscriminatorValueKind discriminatorValueKind = getDiscriminatorValueKind(typeInfo);
                element.annotate(SerdeConfig.class, builder -> {
                    final String typeName = element.stringValue(JsonTypeName.class).orElseGet(() ->
                          discriminatorValueKind == SerdeConfig.Subtyped.DiscriminatorValueKind.CLASS_NAME ? element.getName() : element.getSimpleName()
                    );
                    String typeProperty = resolveTypeProperty(superType).orElseGet(() ->
                       discriminatorValueKind == SerdeConfig.Subtyped.DiscriminatorValueKind.CLASS_NAME ? "@class" : "@type"
                    );
                    final JsonTypeInfo.As include = resolveInclude(superType).orElse(null);
                    handleSubtypeInclude(builder, typeName, typeProperty, include);
                });
            }

            element.findAnnotation(JsonTypeInfo.class).ifPresent((typeInfo) -> {
                if (creatorMode == SerdeConfig.CreatorMode.DELEGATING) {
                    context.fail("Inheritance cannot be combined with DELEGATING creation", element);
                    return;
                }
                final JsonTypeInfo.Id use = typeInfo.enumValue("use", JsonTypeInfo.Id.class).orElse(null);
                final JsonTypeInfo.As include = typeInfo.enumValue("include", JsonTypeInfo.As.class)
                        .orElse(JsonTypeInfo.As.WRAPPER_OBJECT);
                typeInfo.stringValue("defaultImpl").ifPresent(di ->
                      element.annotate(DefaultImplementation.class, (builder) ->
                              builder.member(
                                      AnnotationMetadata.VALUE_MEMBER,
                                      new AnnotationClassValue<>(di)
                              ))
                );

                switch (include) {
                case PROPERTY:
                case WRAPPER_OBJECT:
                    element.annotate(SerdeConfig.Subtyped.class, (builder) -> {
                        builder.member(SerdeConfig.Subtyped.DISCRIMINATOR_TYPE, include.name());
                    });
                    break;
                default:
                    context.fail("Only 'include' of type PROPERTY or WRAPPER_OBJECT are supported", element);
                }
                if (use == null) {
                    context.fail("You must specify 'use' member when using @JsonTypeInfo", element);
                } else {
                    switch (use) {
                    case CLASS:
                    case NAME:
                        element.annotate(SerdeConfig.Subtyped.class, (builder) -> {
                            builder.member(SerdeConfig.Subtyped.DISCRIMINATOR_VALUE, use.name());
                            final String property = typeInfo.stringValue("property")
                                    .orElseGet(() ->
                                                       use == JsonTypeInfo.Id.CLASS ? "@class" : "@type"
                                    );
                            builder.member(SerdeConfig.Subtyped.DISCRIMINATOR_PROP, property);
                        });
                        break;
                    default:
                        context.fail("Only 'use' of type CLASS or NAME are supported", element);
                    }
                }
            });
        }
    }

    private void handleSubtypeInclude(AnnotationValueBuilder<SerdeConfig> builder, String typeName, String typeProperty, JsonTypeInfo.As include) {
        if (include == JsonTypeInfo.As.WRAPPER_OBJECT) {
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
                                   boolean allowSetters) {
        final Set<String> ignoredSet = CollectionUtils.setOf(ignoresProperties);
        final Set<String> includeSet = CollectionUtils.setOf(includeProperties);
        for (TypedElement beanProperty : beanProperties) {
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

    private void resetForNewClass() {
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

    private Optional<ClassElement> findTypeInfo(ClassElement element) {
        // TODO: support interfaces
        if (element.hasDeclaredAnnotation(JsonTypeInfo.class)) {
            return Optional.of(element);
        }

        final ClassElement superElement = element.getSuperType().orElse(null);
        if (superElement == null) {
            return Optional.empty();
        }
        if (superElement.hasDeclaredAnnotation(JsonTypeInfo.class)) {
            return Optional.of(superElement);
        } else {
            return findTypeInfo(superElement);
        }
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private Optional<String> resolveTypeProperty(Optional<ClassElement> superType) {
        return superType.flatMap(st -> {
            final String property = st.stringValue(JsonTypeInfo.class, "property")
                    .orElse(null);
            if (property != null) {
                return Optional.of(property);
            } else {
                return resolveTypeProperty(st.getSuperType());
            }
        });
    }

    private Optional<JsonTypeInfo.As> resolveInclude(Optional<ClassElement> superType) {
        return superType.flatMap(st -> {
            final JsonTypeInfo.As asValue = st.enumValue(JsonTypeInfo.class, "include", JsonTypeInfo.As.class)
                    .orElse(null);
            if (asValue != null) {
                return Optional.of(asValue);
            } else {
                return resolveInclude(st.getSuperType());
            }
        });
    }

    @Override
    public int getOrder() {
        return IntrospectedTypeElementVisitor.POSITION + 100;
    }

    private boolean isJsonAnnotated(ClassElement element) {
        return Stream.of(
                        JsonClassDescription.class,
                        JsonTypeInfo.class,
                        JsonRootName.class,
                        JsonTypeName.class,
                        JsonTypeId.class,
                        JsonAutoDetect.class)
                .anyMatch(element::hasDeclaredAnnotation) ||
                (element.hasStereotype(Serdeable.Serializable.class) || element.hasStereotype(Serdeable.Deserializable.class));
    }

    @Override
    public VisitorKind getVisitorKind() {
        return VisitorKind.ISOLATING;
    }
}
