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
package io.micronaut.json.generator.symbol.bean;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.ast.groovy.visitor.GroovyVisitorContext;
import io.micronaut.core.annotation.AnnotatedElement;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.json.annotation.CustomSerializer;
import io.micronaut.json.annotation.RecursiveSerialization;
import io.micronaut.json.generator.symbol.GeneratorType;
import io.micronaut.json.generator.symbol.ProblemReporter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class BeanIntrospector {
    /**
     * @param problemReporter    Where to output problems
     * @param clazz              Class to introspect
     * @param annotationMetadata Annotation metadata for the class
     * @param forSerialization   Whether this introspection is intended for serialization or deserialization
     * @return The introspection result
     */
    public static BeanDefinition introspect(ProblemReporter problemReporter, VisitorContext context, ClassElement clazz, AnnotationMetadata annotationMetadata, boolean forSerialization) {
        BeanDefinition beanDefinition = new BeanDefinition();
        AnnotationValue<JsonTypeInfo> jsonTypeInfo = annotationMetadata.getAnnotation(JsonTypeInfo.class);
        if (jsonTypeInfo != null) {
            beanDefinition.subtyping = parseSubtyping(problemReporter, context, clazz, jsonTypeInfo, annotationMetadata.getAnnotation(JsonSubTypes.class));
            return beanDefinition;
        }

        Scanner scanner = new Scanner(problemReporter, forSerialization);
        try {
            scanner.groovyContext = context instanceof GroovyVisitorContext;
        } catch (NoClassDefFoundError e) {
            scanner.groovyContext = false;
        }
        scanner.scan(clazz, annotationMetadata);
        Map<PropBuilder, BeanDefinition.Property> completeProps = new LinkedHashMap<>();
        for (PropBuilder prop : scanner.byName.values()) {
            // remove accessors marked as @JsonIgnore
            prop.trimIgnore(forSerialization);
            // remove hidden accessors
            prop.trimInaccessible(forSerialization);
            // filter out properties based on whether they're read/write-only
            if (!prop.shouldInclude(forSerialization)) {
                continue;
            }
            BeanDefinition.Property built;
            if (forSerialization) {
                if (prop.getter != null) {
                    built = BeanDefinition.Property.getter(prop.name, prop.getter.accessor);
                } else {
                    assert prop.field != null;
                    built = BeanDefinition.Property.field(prop.name, prop.field.accessor);
                }
            } else {
                if (prop.creatorParameter != null) {
                    built = BeanDefinition.Property.creatorParameter(clazz, prop.name, prop.creatorParameter);
                } else if (prop.setter != null) {
                    built = BeanDefinition.Property.setter(prop.name, prop.setter.accessor);
                } else {
                    assert prop.field != null;
                    built = BeanDefinition.Property.field(prop.name, prop.field.accessor);
                }
            }
            GeneratorType type = built.getType(Function.identity());
            if (type.getClassLevelAnnotations().getAnnotation(JsonIgnoreType.class) != null) {
                continue;
            }

            built.permitRecursiveSerialization = prop.permitRecursiveSerialization;
            built.nullable = prop.nullable;
            built.unwrapped = prop.unwrapped;
            built.required = prop.required;
            built.anyGetter = prop.anyGetter;
            built.aliases = prop.aliases;
            built.valueInclusionPolicy = prop.valueInclusionPolicy;
            built.viewClasses = prop.viewClassNames == null ?
                    null :
                    prop.viewClassNames.stream().map(fqcn -> context.getClassElement(fqcn).get()).collect(Collectors.toList());
            built.customSerializer = prop.customSerializerFqcn == null ? null : context.getClassElement(prop.customSerializerFqcn).get();
            built.customDeserializer = prop.customDeserializerFqcn == null ? null : context.getClassElement(prop.customDeserializerFqcn).get();
            completeProps.put(prop, built);
        }
        beanDefinition.props = new ArrayList<>(completeProps.values());
        if (!forSerialization) {
            beanDefinition.creator = scanner.creator;
            if (scanner.creatorDelegatingProperty == null) {
                beanDefinition.creatorProps = scanner.creatorProps.stream().map(completeProps::get).collect(Collectors.toList());
            } else {
                beanDefinition.creatorDelegatingProperty = completeProps.get(scanner.creatorDelegatingProperty);
            }
        }
        beanDefinition.ignoreUnknownProperties = scanner.ignoreUnknownProperties;
        beanDefinition.valueProperty = completeProps.get(scanner.valueProperty);
        beanDefinition.anySetter = scanner.anySetter;
        return beanDefinition;
    }

    private static BeanDefinition.Subtyping parseSubtyping(
            ProblemReporter problemReporter,
            VisitorContext context,
            ClassElement on,
            @NonNull AnnotationValue<JsonTypeInfo> jsonTypeInfo,
            @Nullable AnnotationValue<JsonSubTypes> jsonSubTypes
    ) {
        BeanDefinition.Subtyping subtyping = new BeanDefinition.Subtyping();
        if (jsonSubTypes == null) {
            problemReporter.fail("Subtype handling must know all sub types in advance. Please annotate with @JsonSubTypes as well", on);
            return subtyping;
        }
        JsonTypeInfo.Id use = jsonTypeInfo.get("use", JsonTypeInfo.Id.class).get();
        // fqcn -> explicit names
        Map<String, Collection<String>> explicitTypes = new LinkedHashMap<>();
        for (AnnotationValue<JsonSubTypes.Type> explicitType : jsonSubTypes.<JsonSubTypes.Type>getAnnotations("value")) {
            String fqcn = explicitType.annotationClassValue("value").get().getName();
            Collection<String> explicitNames = explicitTypes.computeIfAbsent(fqcn, k -> new LinkedHashSet<>());
            explicitType.stringValue("name").ifPresent(explicitNames::add);
            Collections.addAll(explicitNames, explicitType.stringValues("names"));
        }
        subtyping.subTypes = explicitTypes.keySet().stream()
                .map(fqcn -> GeneratorType.ofClass(context.getClassElement(fqcn).get()))
                .collect(Collectors.toSet());
        switch (use) {
            case CLASS:
                subtyping.subTypeNames = subtyping.subTypes.stream()
                        .collect(Collectors.toMap(cls -> cls, cls -> Collections.singleton(cls.getTypeName())));
                break;
            case MINIMAL_CLASS:
                String basePackage = on.getPackageName();
                subtyping.subTypeNames = subtyping.subTypes.stream()
                        .collect(Collectors.toMap(cls -> cls, cls -> {
                            String fqcn = cls.getTypeName();
                            if (fqcn.startsWith(basePackage + '.')) {
                                return Collections.singleton(fqcn.substring(basePackage.length()));
                            } else {
                                return Collections.singleton(fqcn);
                            }
                        }));
                break;
            case NAME:
                subtyping.subTypeNames = subtyping.subTypes.stream()
                        .collect(Collectors.toMap(cls -> cls, cls -> {
                            Collection<String> names = explicitTypes.get(cls.getTypeName());
                            if (names.isEmpty()) {
                                ClassElement rawClassElement = cls.getRawClass();
                                AnnotationValue<JsonTypeName> jsonTypeName = rawClassElement.getAnnotation(JsonTypeName.class);
                                if (jsonTypeName != null) {
                                    names = Collections.singleton(jsonTypeName.stringValue().map(s -> s.isEmpty() ? null : s).orElse(rawClassElement.getSimpleName()));
                                } else {
                                    problemReporter.fail("No explicit name given for type " + rawClassElement.getName(), on);
                                }
                            }
                            return names;
                        }));
                break;
            case DEDUCTION:
                subtyping.deduce = true;
                break;
            default:
                problemReporter.fail("Unsupported type id kind: " + use, on);
                return subtyping;
        }
        subtyping.as = jsonTypeInfo.get("include", JsonTypeInfo.As.class, JsonTypeInfo.As.PROPERTY);
        subtyping.propertyName = jsonTypeInfo.stringValue("property").orElse("");
        Optional<AnnotationClassValue<?>> defaultImpl = jsonTypeInfo.annotationClassValue("defaultImpl");
        if (defaultImpl.isPresent() && !defaultImpl.get().getName().equals(JsonTypeInfo.class.getName())) {
            if (!defaultImpl.get().getName().equals(Void.class.getName())) {
                problemReporter.fail("Mapping unknown types to null is not supported", on);
                return subtyping;
            }
            subtyping.defaultImpl = GeneratorType.ofClass(context.getClassElement(defaultImpl.get().getName()).get());
        }
        return subtyping;
    }

    private static String decapitalize(String s) {
        if (s.isEmpty()) {
            return "";
        }
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    /**
     * mostly follows jackson-jr AnnotationBasedIntrospector.
     */
    private static class Scanner {
        final ProblemReporter problemReporter;
        final boolean forSerialization;

        boolean groovyContext;

        final Map<String, PropBuilder> byImplicitName = new LinkedHashMap<>();
        Map<String, PropBuilder> byName;

        MethodElement anySetter;

        MethodElement creator = null;
        /**
         * Properties for the {@link #creator} parameters.
         */
        List<PropBuilder> creatorProps;
        /**
         * If the creator is delegating, the property for the single parameter.
         * <p>
         * We use a "fake" property here, even though it's a bit unlike other properties, so that we can use modifying
         * annotations, e.g. for null handling.
         */
        PropBuilder creatorDelegatingProperty;

        /**
         * Single property annotated with @JsonValue
         */
        PropBuilder valueProperty;

        boolean ignoreUnknownProperties;
        JsonInclude.Include defaultInclusionPolicy;

        JsonAutoDetect.Visibility fieldVisibility;
        JsonAutoDetect.Visibility getterVisibility;
        JsonAutoDetect.Visibility isGetterVisibility;
        JsonAutoDetect.Visibility setterVisibility;

        Scanner(ProblemReporter problemReporter, boolean forSerialization) {
            this.problemReporter = problemReporter;
            this.forSerialization = forSerialization;
        }

        private PropBuilder getByImplicitName(String implicitName) {
            return byImplicitName.computeIfAbsent(implicitName, s -> new PropBuilder());
        }

        private PropBuilder getByName(String name) {
            return byName.computeIfAbsent(name, s -> {
                PropBuilder prop = new PropBuilder();
                prop.name = s;
                return prop;
            });
        }

        private String getExplicitName(AnnotatedElement element) {
            AnnotationValue<JsonProperty> jsonProperty = element.getAnnotation(JsonProperty.class);
            if (jsonProperty != null) {
                return jsonProperty.getValue(String.class).orElse("");
            }
            return null;
        }

        /**
         * @return Whether this element has an annotation that explicitly designates it as a property
         */
        private boolean hasPropertyAnnotation(AnnotatedElement element) {
            return element.isAnnotationPresent(JsonProperty.class) ||
                    element.isAnnotationPresent(JsonValue.class) ||
                    element.isAnnotationPresent(JsonAnyGetter.class) ||
                    element.isAnnotationPresent(JsonUnwrapped.class);
        }

        private boolean isIgnore(AnnotatedElement element) {
            AnnotationValue<JsonIgnore> ignore = element.getAnnotation(JsonIgnore.class);
            if (ignore == null) {
                return false;
            }
            Optional<Boolean> value = ignore.getValue(Boolean.class);
            return value.orElse(true);
        }

        private <T extends Element> Accessor<T> makeAccessor(T element, String implicitName) {
            String explicitName = getExplicitName(element);
            String finalName = implicitName;
            AccessorType type;
            if (isIgnore(element)) {
                type = AccessorType.IGNORABLE;
            } else if (explicitName == null) {
                type = AccessorType.IMPLICIT;
            } else if (explicitName.isEmpty()) {
                type = AccessorType.VISIBLE;
            } else {
                type = AccessorType.EXPLICIT;
                finalName = explicitName;
            }
            return new Accessor<>(finalName, element, type);
        }

        private JsonAutoDetect.Visibility getVisibility(@Nullable AnnotationValue<JsonAutoDetect> jsonAutoDetect, String name, JsonAutoDetect.Visibility defaultValue) {
            if (jsonAutoDetect != null) {
                Optional<JsonAutoDetect.Visibility> configured = jsonAutoDetect.get(name, JsonAutoDetect.Visibility.class);
                if (configured.isPresent() && configured.get() != JsonAutoDetect.Visibility.DEFAULT) {
                    return configured.get();
                }
            }
            return defaultValue;
        }

        private boolean isVisibleForAutoDetect(Element element, JsonAutoDetect.Visibility visibility) {
            switch (visibility) {
                case NONE:
                    return false;
                case PUBLIC_ONLY:
                    if (element.isProtected()) {
                        return false;
                    }
                    // fall-through
                case PROTECTED_AND_PUBLIC:
                    if (element.isPackagePrivate()) {
                        return false;
                    }
                    // fall-through
                case NON_PRIVATE:
                    if (element.isPrivate()) {
                        return false;
                    }
                    // fall-through
                case ANY:
                    return true;
                default:
                    throw new AssertionError(visibility);
            }
        }

        void scan(ClassElement clazz, AnnotationMetadata annotationMetadata) {
            AnnotationValue<JsonIgnoreProperties> jsonIgnoreProperties = annotationMetadata.getAnnotation(JsonIgnoreProperties.class);
            ignoreUnknownProperties = true;
            if (jsonIgnoreProperties != null) {
                ignoreUnknownProperties = jsonIgnoreProperties.get("ignoreUnknown", Boolean.class, ignoreUnknownProperties);
            }

            AnnotationValue<JsonInclude> jsonInclude = annotationMetadata.getAnnotation(JsonInclude.class);
            defaultInclusionPolicy = JsonInclude.Include.NON_EMPTY; // match JacksonConfiguration default behavior
            if (jsonInclude != null) {
                defaultInclusionPolicy = jsonInclude.get("value", JsonInclude.Include.class, defaultInclusionPolicy);
            }

            AnnotationValue<JsonAutoDetect> jsonAutoDetect = annotationMetadata.getAnnotation(JsonAutoDetect.class);
            fieldVisibility = getVisibility(jsonAutoDetect, "fieldVisibility", JsonAutoDetect.Visibility.PUBLIC_ONLY);
            getterVisibility = getVisibility(jsonAutoDetect, "getterVisibility", JsonAutoDetect.Visibility.PUBLIC_ONLY);
            isGetterVisibility = getVisibility(jsonAutoDetect, "isGetterVisibility", JsonAutoDetect.Visibility.PUBLIC_ONLY);
            setterVisibility = getVisibility(jsonAutoDetect, "setterVisibility", JsonAutoDetect.Visibility.PUBLIC_ONLY);

            // todo: check we don't have another candidate when replacing properties of the definition

            for (FieldElement field : clazz.getEnclosedElements(ElementQuery.ALL_FIELDS.onlyInstance())) {
                if (hasPropertyAnnotation(field) || isVisibleForAutoDetect(field, fieldVisibility)) {
                    PropBuilder prop = getByImplicitName(field.getName());
                    prop.field = makeAccessor(field, field.getName());
                }
            }

            for (MethodElement method : clazz.getEnclosedElements(ElementQuery.ALL_METHODS.onlyInstance())) {
                // if we have an explicit @JsonProperty, fall back to just the method name as the implicit name
                if (method.getParameters().length == 0) {
                    // getter
                    boolean consider = hasPropertyAnnotation(method);

                    String implicitName = method.getName();
                    if (implicitName.startsWith("get")) {
                        if (isVisibleForAutoDetect(method, getterVisibility)) {
                            implicitName = decapitalize(implicitName.substring(3));
                            consider = true;
                        }
                    } else if (implicitName.startsWith("is")) {
                        if (isVisibleForAutoDetect(method, isGetterVisibility)) {
                            implicitName = decapitalize(implicitName.substring(2));
                            consider = true;
                        }
                    }

                    if (consider) {
                        PropBuilder prop = getByImplicitName(implicitName);
                        prop.getter = makeAccessor(method, implicitName);
                    }
                } else if (method.getParameters().length == 1) {
                    // setter
                    boolean consider = hasPropertyAnnotation(method);

                    String implicitName = method.getName();
                    if (implicitName.startsWith("set")) {
                        if (isVisibleForAutoDetect(method, setterVisibility)) {
                            implicitName = decapitalize(implicitName.substring(3));
                            consider = true;
                        }
                    }

                    if (consider) {
                        PropBuilder prop = getByImplicitName(implicitName);
                        prop.setter = makeAccessor(method, implicitName);
                    }
                }
            }

            // in java, properties are handled through method scanning. Groovy properties are different.
            if (groovyContext) {
                for (PropertyElement beanProperty : clazz.getBeanProperties()) {
                    String implicitName = beanProperty.getName();
                    beanProperty.getReadMethod().ifPresent(getter -> {
                        if (hasPropertyAnnotation(getter) || hasPropertyAnnotation(beanProperty) ||
                                isVisibleForAutoDetect(getter, getterVisibility)) {
                            getByImplicitName(implicitName).getter = makeAccessor(getter, implicitName);
                        }
                    });
                    beanProperty.getWriteMethod().ifPresent(setter -> {
                        if (hasPropertyAnnotation(setter) || hasPropertyAnnotation(beanProperty) ||
                                isVisibleForAutoDetect(setter, getterVisibility)) {
                            getByImplicitName(implicitName).setter = makeAccessor(setter, implicitName);
                        }
                    });
                }
            }

            byName = new LinkedHashMap<>();
            for (Map.Entry<String, PropBuilder> entry : byImplicitName.entrySet()) {
                PropBuilder prop = entry.getValue();
                String explicitName = prop.accessorsInOrder(forSerialization)
                        .filter(acc -> acc.type == AccessorType.EXPLICIT)
                        .findFirst()
                        .map(acc -> acc.name)
                        .orElse(null);
                prop.name = explicitName == null ? entry.getKey() : explicitName;
                byName.put(prop.name, prop);
            }

            if (!forSerialization) {
                for (MethodElement method : clazz.getEnclosedElements(ElementQuery.ALL_METHODS.annotated(m -> m.hasAnnotation(JsonCreator.class)))) {
                    handleCreator(method, true);
                }
                for (ConstructorElement constructor : clazz.getEnclosedElements(ElementQuery.of(ConstructorElement.class).annotated(m -> m.hasAnnotation(JsonCreator.class)))) {
                    handleCreator(constructor, true);
                }

                if (creator == null) {
                    MethodElement defaultConstructor = clazz.getDefaultConstructor().orElse(null);
                    if (defaultConstructor != null) {
                        // use the default constructor as an "empty creator"
                        handleCreator(defaultConstructor, false);
                    } else {
                        MethodElement primaryConstructor = clazz.getPrimaryConstructor().orElse(null);
                        if (primaryConstructor != null) {
                            handleCreator(primaryConstructor, false);
                        } else {
                            problemReporter.fail("Missing constructor for deserialization " + clazz.getName(), clazz);
                        }
                    }
                }
            }

            List<MethodElement> anySetters = clazz.getEnclosedElements(ElementQuery.ALL_METHODS.annotated(m -> m.hasAnnotation(JsonAnySetter.class)));
            if (!anySetters.isEmpty()) {
                if (anySetters.size() > 1) {
                    problemReporter.fail("Only one @JsonAnySetter allowed", anySetters.get(1));
                    return;
                }
                anySetter = anySetters.get(0);
                if (anySetter.getParameters().length != 2) {
                    problemReporter.fail("@JsonAnySetter must have exactly two parameters", anySetter);
                    return;
                }
            }

            for (PropBuilder prop : byName.values()) {
                if (prop.annotatedElementsInOrder(forSerialization)
                        .anyMatch(element -> element.hasAnnotation(JsonValue.class))) {
                    if (valueProperty != null) {
                        problemReporter.fail("Multiple properties annotated with @JsonValue",
                                prop.annotatedElementsInOrder(forSerialization).findFirst().get());
                    }
                    valueProperty = prop;
                }

                // if there's a @RecursiveSerialization on *any* of the involved elements, mark the property for recursive ser
                prop.permitRecursiveSerialization = prop.annotatedElementsInOrder(forSerialization)
                        .anyMatch(element -> element.hasAnnotation(RecursiveSerialization.class));

                // infer nullable support from the first @Nullable/@NonNull annotation we find
                prop.nullable = prop.annotatedElementsInOrder(forSerialization)
                        .map(element -> {
                            if (element.isNullable()) {
                                return true;
                            } else if (element.isNonNull()) {
                                return false;
                            } else {
                                return null;
                            }
                        })
                        .filter(Objects::nonNull)
                        .findFirst().orElse(null);

                prop.unwrapped = prop.annotatedElementsInOrder(forSerialization)
                        .anyMatch(element -> element.hasAnnotation(JsonUnwrapped.class));

                if (prop.unwrapped && prop.permitRecursiveSerialization) {
                    //noinspection OptionalGetWithoutIsPresent
                    problemReporter.fail("Cannot combine @RecursiveSerialization with @JsonUnwrapped",
                            prop.annotatedElementsInOrder(forSerialization).findFirst().get());
                }

                prop.aliases = prop.annotatedElementsInOrder(forSerialization)
                        .flatMap(element -> {
                            AnnotationValue<JsonAlias> aliasAnnotation = element.getAnnotation(JsonAlias.class);
                            if (aliasAnnotation == null) {
                                return Stream.empty();
                            } else {
                                return Stream.of(aliasAnnotation.stringValues());
                            }
                        })
                        .collect(Collectors.toSet());

                prop.valueInclusionPolicy = prop.annotatedElementsInOrder(forSerialization)
                        .map(element -> element.getAnnotation(JsonInclude.class))
                        .filter(Objects::nonNull)
                        .findFirst()
                        // if @JsonInclude is specified parameterless, pick ALWAYS. if it is not specified, pick USE_DEFAULTS.
                        .map(annotation -> annotation.get("value", JsonInclude.Include.class, JsonInclude.Include.ALWAYS))
                        .orElse(JsonInclude.Include.USE_DEFAULTS);
                if (prop.valueInclusionPolicy == JsonInclude.Include.USE_DEFAULTS) {
                    prop.valueInclusionPolicy = defaultInclusionPolicy;
                }

                prop.required = prop.annotatedElementsInOrder(forSerialization)
                        .map(element -> element.getAnnotation(JsonProperty.class))
                        .filter(Objects::nonNull)
                        .map(annotation -> annotation.booleanValue("required"))
                        .findFirst()
                        .orElse(Optional.empty()).orElse(false);

                prop.anyGetter = prop.annotatedElementsInOrder(forSerialization)
                        .anyMatch(e -> e.hasAnnotation(JsonAnyGetter.class));

                prop.viewClassNames = Stream.concat(prop.annotatedElementsInOrder(forSerialization), Stream.of(clazz))
                        .map(e -> e.getAnnotation(JsonView.class))
                        .filter(Objects::nonNull)
                        .map(a -> Arrays.stream(a.annotationClassValues("value")).map(AnnotationClassValue::getName).collect(Collectors.toList()))
                        .findFirst().orElse(null);

                Optional<AnnotationValue<CustomSerializer>> customSerializerAnnotation = prop.annotatedElementsInOrder(forSerialization)
                        .map(e -> e.getAnnotation(CustomSerializer.class))
                        .filter(Objects::nonNull).findFirst();
                prop.customSerializerFqcn = customSerializerAnnotation.flatMap(v -> v.annotationClassValue("serializer")).map(AnnotationClassValue::getName).orElse(null);
                prop.customDeserializerFqcn = customSerializerAnnotation.flatMap(v -> v.annotationClassValue("deserializer")).map(AnnotationClassValue::getName).orElse(null);
            }
        }

        private void handleCreator(MethodElement method, boolean explicit) {
            JsonCreator.Mode mode;
            if (explicit) {
                AnnotationValue<JsonCreator> creatorAnnotation = method.getAnnotation(JsonCreator.class);
                assert creatorAnnotation != null;
                mode = creatorAnnotation.get("mode", JsonCreator.Mode.class).orElse(JsonCreator.Mode.DEFAULT);
            } else {
                mode = JsonCreator.Mode.PROPERTIES;
            }

            ParameterElement[] parameters = method.getParameters();
            boolean delegating;
            switch (mode) {
                case DEFAULT:
                    delegating = false;
                    if (parameters.length == 1) {
                        ParameterElement singleParameter = parameters[0];
                        if (singleParameter.getAnnotation(JsonProperty.class) == null) {
                            String implicitParameterName = singleParameter.getName();
                            if (!byName.containsKey(implicitParameterName)) {
                                delegating = true;
                            }
                        }
                    }
                    break;
                case DELEGATING:
                    delegating = true;
                    break;
                case PROPERTIES:
                    delegating = false;
                    break;
                case DISABLED:
                    return; // skip this creator
                default:
                    throw new AssertionError("bad creator mode " + mode);
            }

            // do this check after checking the mode so that DISABLED creators don't lead to an error
            if (!method.isStatic() && !(method instanceof ConstructorElement)) {
                problemReporter.fail("@JsonCreator annotation cannot be placed on instance methods", method);
                return;
            }
            if (creator != null) {
                problemReporter.fail("Multiple creators configured", method);
            }

            creator = method;
            if (delegating) {
                if (parameters.length != 1) {
                    problemReporter.fail("Delegating creator must have exactly one parameter", method);
                    creator = null;
                    return;
                }
                // create a fake property
                creatorDelegatingProperty = getByName(UUID.randomUUID().toString());
                creatorDelegatingProperty.creatorParameter = parameters[0];
            } else {
                creatorProps = new ArrayList<>();
                for (ParameterElement parameter : parameters) {
                    AnnotationValue<JsonProperty> propertyAnnotation = parameter.getAnnotation(JsonProperty.class);
                    // we allow empty property names here, as long as they're explicitly defined.
                    Optional<String> explicitPropName = Optional.ofNullable(propertyAnnotation)
                            .flatMap(annotation -> annotation.getValue(String.class));
                    if (!explicitPropName.isPresent()) {
                        String implicitName = parameter.getName();
                        if (!explicit && !byName.containsKey(implicitName)) {
                            problemReporter.fail("Refusing an implicit bean creator where parameter names do not match bean properties. If this is intentional, annotate this method with @JsonCreator.", method);
                        }
                        explicitPropName = Optional.of(implicitName);
                    }
                    PropBuilder prop = getByName(explicitPropName.get());
                    prop.creatorParameter = parameter;
                    creatorProps.add(prop);
                }
            }
        }
    }

    private static class PropBuilder {
        String name;

        boolean permitRecursiveSerialization;
        Boolean nullable;
        boolean unwrapped;
        boolean required;
        boolean anyGetter;

        JsonInclude.Include valueInclusionPolicy = JsonInclude.Include.USE_DEFAULTS;

        Set<String> aliases;

        @Nullable
        Accessor<FieldElement> field;
        @Nullable
        Accessor<MethodElement> getter;
        @Nullable
        Accessor<MethodElement> setter;
        @Nullable
        ParameterElement creatorParameter;

        @Nullable
        List<String> viewClassNames;

        String customSerializerFqcn;
        String customDeserializerFqcn;

        void trimInaccessible(boolean forSerialization) {
            if (getter != null && !getter.isAccessible()) {
                getter = null;
            }
            if (setter != null && !setter.isAccessible()) {
                setter = null;
            }
            if (field != null && (!field.isAccessible() || (!forSerialization && field.accessor.isFinal()))) {
                field = null;
            }
        }

        void trimIgnore(boolean forSerialization) {
            // First, remove accessors that are marked as @JsonIgnore.
            // Only consider accessors relevant for the serialization direction.
            boolean anyIgnore = false;
            if (forSerialization && getter != null && getter.type == AccessorType.IGNORABLE) {
                getter = null;
                anyIgnore = true;
            }
            if (!forSerialization && setter != null && setter.type == AccessorType.IGNORABLE) {
                setter = null;
                anyIgnore = true;
            }
            if (field != null && field.type == AccessorType.IGNORABLE) {
                field = null;
                anyIgnore = true;
            }
            // If *any* of the accessors were ignored, was there an explicit @JsonProperty on the other accessor?
            // If not, remove that accessor as well.
            if (anyIgnore) {
                boolean anyExplicit = Stream.of(field, forSerialization ? getter : setter)
                        .filter(Objects::nonNull)
                        .anyMatch(acc -> acc.type == AccessorType.EXPLICIT || acc.type == AccessorType.VISIBLE);
                if (!anyExplicit) {
                    field = null;
                    getter = null;
                    setter = null;
                    // note: the property could still be usable if it is a @JsonCreator parameter.
                }
            }
        }

        boolean shouldInclude(boolean forSerialization) {
            // if the accessors weren't accessible, they were already removed in trimAccessible
            // todo: error when a property is inaccessible because the user forgot to give access
            if (forSerialization) {
                return getter != null || field != null;
            } else {
                return setter != null || field != null || creatorParameter != null;
            }
        }

        Stream<Accessor<? extends MemberElement>> accessorsInOrder(boolean forSerialization) {
            return (forSerialization ? Stream.of(getter, setter, field) : Stream.of(setter, getter, field)).filter(Objects::nonNull);
        }

        /**
         * Get the elements for this property that should be scanned for annotations, in order of priority.
         */
        Stream<Element> annotatedElementsInOrder(boolean forSerialization) {
            Stream<Element> stream = accessorsInOrder(forSerialization).flatMap(a -> {
                if (a == setter) {
                    // the single parameter of the setter may also be annotated (e.g. as @Nullable)
                    assert setter != null;
                    return Stream.of(setter.accessor, setter.accessor.getParameters()[0]);
                } else {
                    return Stream.of(a.accessor);
                }
            });
            if (creatorParameter != null) {
                if (forSerialization) {
                    stream = Stream.concat(stream, Stream.of(creatorParameter));
                } else {
                    stream = Stream.concat(Stream.of(creatorParameter), stream);
                }
            }
            return stream;
        }
    }

    private static class Accessor<T extends Element> {
        final String name;
        final T accessor;
        final AccessorType type;

        Accessor(String name, T accessor, AccessorType type) {
            this.name = name;
            this.accessor = accessor;
            this.type = type;
        }

        boolean isAccessible() {
            // serializers are always in the same package right now
            return !accessor.isPrivate();
        }
    }

    private enum AccessorType {
        /**
         * {@literal @}{@link JsonIgnore}.
         */
        IGNORABLE,
        /**
         * Looks like an accessor.
         */
        IMPLICIT,
        /**
         * {@literal @}{@link JsonProperty} without name.
         */
        VISIBLE,
        /**
         * {@literal @}{@link JsonProperty} with name.
         */
        EXPLICIT,
    }
}
