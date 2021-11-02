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
package io.micronaut.serde.processor.jackson;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.annotation.JsonTypeId;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.micronaut.context.annotation.DefaultImplementation;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.beans.visitor.IntrospectedTypeElementVisitor;
import io.micronaut.inject.visitor.TypeElementVisitor;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.annotation.SerdeConfig;
import io.micronaut.serde.annotation.Serdeable;

public class JacksonAnnotationVisitor implements TypeElementVisitor<SerdeConfig, SerdeConfig> {

    @Override
    public Set<String> getSupportedAnnotationNames() {
        return CollectionUtils.setOf(
                "com.fasterxml.jackson.annotation.*",
                "jakarta.json.bind.annotation.*",
                "io.micronaut.serde.annotation.*",
                "org.bson.codecs.pojo.annotations.*"
        );
    }

    @Override
    public void visitClass(ClassElement element, VisitorContext context) {
        if (isJsonAnnotated(element)) {
            if (!element.hasStereotype(Serdeable.Serializable.class) &&
                    !element.hasStereotype(Serdeable.Deserializable.class)) {
                element.annotate(Serdeable.class);
                element.annotate(Introspected.class, (builder) -> {
                    builder.member("accessKind", Introspected.AccessKind.METHOD, Introspected.AccessKind.FIELD);
                    builder.member("visibility", "PUBLIC");
                });
            }

            final Optional<ClassElement> superType = findTypeInfo(element);
            if (superType.isPresent()) {
                final ClassElement typeInfo = superType.get();
                final SerdeConfig.Subtyped.DiscriminatorValueKind discriminatorValueKind = getDiscriminatorValueKind(typeInfo);
                element.annotate(SerdeConfig.class, builder -> {
                    final String typeName = element.stringValue(JsonTypeName.class).orElseGet(() ->
                          discriminatorValueKind == SerdeConfig.Subtyped.DiscriminatorValueKind.CLASS ? element.getName() : element.getSimpleName()
                    );
                    String typeProperty = resolveTypeProperty(superType).orElseGet(() ->
                       discriminatorValueKind == SerdeConfig.Subtyped.DiscriminatorValueKind.CLASS ? "@class" : "@type"
                    );
                    builder.member(SerdeConfig.TYPE_NAME, typeName);
                    builder.member(SerdeConfig.TYPE_PROPERTY, typeProperty);
                });
            }

            element.findAnnotation(JsonTypeInfo.class).ifPresent((typeInfo) -> {
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

    private SerdeConfig.Subtyped.DiscriminatorValueKind getDiscriminatorValueKind(ClassElement typeInfo) {
        return typeInfo.enumValue(
                SerdeConfig.Subtyped.class,
                SerdeConfig.Subtyped.DISCRIMINATOR_VALUE,
                SerdeConfig.Subtyped.DiscriminatorValueKind.class)
                .orElse(SerdeConfig.Subtyped.DiscriminatorValueKind.CLASS);
    }

    private Optional<ClassElement> findTypeInfo(ClassElement element) {
        // TODO: support interfaces
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
