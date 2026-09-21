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

import io.micronaut.context.annotation.DefaultImplementation;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.config.annotation.SerdeConfig;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Mapper for JsonTypeInfo.
 */
public class JsonTypeInfoMapper extends ValidatingAnnotationMapper {
    private static final String PROPERTY_MEMBER = "property";
    private static final String INCLUDE_MEMBER = "include";
    private static final String VISIBLE_MEMBER = "visible";

    @Override
    public String getName() {
        return "com.fasterxml.jackson.annotation.JsonTypeInfo";
    }

    @Override
    protected Set<String> getSupportedMemberNames() {
        return CollectionUtils.setOf(
                "defaultImpl",
                PROPERTY_MEMBER,
                INCLUDE_MEMBER,
                "use",
                VISIBLE_MEMBER
        );
    }

    @Override
    protected List<AnnotationValue<?>> mapValid(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        String use = annotation.stringValue("use").orElse(null);
        if (use == null) {
            return mapError("You must specify 'use' member when using @JsonTypeInfo");
        }
        List<AnnotationValue<?>> values = new ArrayList<>(2);
        AnnotationValueBuilder<SerdeConfig.SerSubtyped> builder = AnnotationValue.builder(SerdeConfig.SerSubtyped.class);

        AnnotationClassValue<?> defaultImpl = annotation.annotationClassValue("defaultImpl").orElse(null);
        if (defaultImpl != null) {
            if (!defaultImpl.getName().equals("com.fasterxml.jackson.annotation.JsonTypeInfo")) {
                values.add(
                    AnnotationValue.builder(DefaultImplementation.class)
                        .member(AnnotationMetadata.VALUE_MEMBER, defaultImpl)
                        .build()
                );
            }
            builder.member(SerdeConfig.SerSubtyped.DEFAULT_IMPL, defaultImpl);
        }

        if ("DEDUCTION".equals(use)) {
            builder.member(SerdeConfig.SerSubtyped.DISCRIMINATOR_VALUE, SerdeConfig.SerSubtyped.DiscriminatorValueKind.DEDUCTION);
            if (annotation.booleanValue(VISIBLE_MEMBER).isPresent()) {
                return mapError("JsonTypeInfo with DEDUCTION strategy doesn't support: 'visible'");
            }
            if (annotation.stringValue(PROPERTY_MEMBER).isPresent()) {
                return mapError("JsonTypeInfo with DEDUCTION strategy doesn't support: 'property'");
            }
            if (annotation.stringValue(INCLUDE_MEMBER).isPresent()) {
                return mapError("JsonTypeInfo with DEDUCTION strategy doesn't support: 'include'");
            }
            // visible, include and property are not allowed
        } else {
            builder.member(
                SerdeConfig.SerSubtyped.DISCRIMINATOR_VISIBLE,
                annotation.booleanValue(VISIBLE_MEMBER).orElse(false)
            );
            String include = annotation.stringValue(INCLUDE_MEMBER).orElse("PROPERTY");
            builder.member(SerdeConfig.SerSubtyped.DISCRIMINATOR_TYPE, include);

            Optional<String> propertyValue = annotation.stringValue(PROPERTY_MEMBER);
            switch (use) {
                case "CLASS" -> {
                    builder.member(SerdeConfig.SerSubtyped.DISCRIMINATOR_VALUE, SerdeConfig.SerSubtyped.DiscriminatorValueKind.CLASS_NAME);
                    builder.member(SerdeConfig.SerSubtyped.DISCRIMINATOR_PROP, propertyValue.orElse("@class"));
                }
                case "NAME" -> {
                    builder.member(SerdeConfig.SerSubtyped.DISCRIMINATOR_VALUE, SerdeConfig.SerSubtyped.DiscriminatorValueKind.NAME);
                    builder.member(SerdeConfig.SerSubtyped.DISCRIMINATOR_PROP, propertyValue.orElse("@type"));
                }
                case "MINIMAL_CLASS" -> {
                    builder.member(SerdeConfig.SerSubtyped.DISCRIMINATOR_VALUE, SerdeConfig.SerSubtyped.DiscriminatorValueKind.MINIMAL_CLASS);
                    builder.member(SerdeConfig.SerSubtyped.DISCRIMINATOR_PROP, propertyValue.orElse("@c"));
                }
                default -> {
                    return mapError("Unsupported JsonTypeInfo use: " + use);
                }
            }
        }
        values.add(builder.build());
        return values;
    }

    private List<AnnotationValue<?>> mapError(String message) {
        return Collections.singletonList(
                AnnotationValue.builder(SerdeConfig.SerError.class)
                        .value(message)
                        .build()
        );
    }
}
