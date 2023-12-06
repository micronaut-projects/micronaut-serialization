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
import java.util.*;

/**
 * Mapper for JsonTypeInfo.
 */
public class JsonTypeInfoMapper extends ValidatingAnnotationMapper {
    @Override
    public String getName() {
        return "com.fasterxml.jackson.annotation.JsonTypeInfo";
    }

    @Override
    protected Set<String> getSupportedMemberNames() {
        return CollectionUtils.setOf(
                "defaultImpl",
                "property",
                "include",
                "use",
                "visible"
        );
    }

    @Override
    protected List<AnnotationValue<?>> mapValid(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        String use = annotation.stringValue("use").orElse(null);
        if (use == null) {
            return mapError("You must specify 'use' member when using @JsonTypeInfo");
        }
        List<AnnotationValue<?>> values = new ArrayList<>(2);

        AnnotationClassValue<?> defaultImpl = annotation.annotationClassValue("defaultImpl").orElse(null);
        if (defaultImpl != null) {
            values.add(
                AnnotationValue.builder(DefaultImplementation.class)
                        .member(AnnotationMetadata.VALUE_MEMBER, defaultImpl)
                        .build()
            );
        }

        AnnotationValueBuilder<SerdeConfig.SerSubtyped> builder = AnnotationValue.builder(SerdeConfig.SerSubtyped.class);
        builder.member(
            SerdeConfig.SerSubtyped.DISCRIMINATOR_VISIBLE,
            annotation.booleanValue("visible").orElse(false)
        );
        String include = annotation.stringValue("include").orElse("PROPERTY");
        switch (include) {
            case "PROPERTY", "WRAPPER_OBJECT", "WRAPPER_ARRAY", "EXISTING_PROPERTY" -> builder.member(SerdeConfig.SerSubtyped.DISCRIMINATOR_TYPE, include);
            default -> {
                return mapError("Only 'include' of type PROPERTY, EXISTING_PROPERTY, WRAPPER_OBJECT or WRAPPER_ARRAY are supported");
            }
        }

        Optional<String> propertyValue = annotation.stringValue("property");
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
