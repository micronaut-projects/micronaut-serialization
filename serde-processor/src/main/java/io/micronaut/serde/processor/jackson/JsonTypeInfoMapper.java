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
                "use"
        );
    }

    @Override
    protected List<AnnotationValue<?>> mapValid(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        String use = annotation.stringValue("use").orElse(null);
        String include = annotation.stringValue("include").orElse("PROPERTY");
        AnnotationClassValue<?> defaultImpl = annotation.annotationClassValue("defaultImpl").orElse(null);
        List<AnnotationValue<?>> values = new ArrayList<>(2);
        if (use == null) {
            return mapError("You must specify 'use' member when using @JsonTypeInfo");
        }

        if (defaultImpl != null) {
            values.add(
                AnnotationValue.builder(DefaultImplementation.class)
                        .member(AnnotationMetadata.VALUE_MEMBER, defaultImpl)
                        .build()
            );
        }

        String property = annotation.stringValue("property").orElse(null);
        AnnotationValueBuilder<SerdeConfig.SerSubtyped> builder = AnnotationValue.builder(SerdeConfig.SerSubtyped.class);
        switch (include) {
            case "PROPERTY":
            case "WRAPPER_OBJECT":
                builder.member(SerdeConfig.SerSubtyped.DISCRIMINATOR_TYPE, include);
                break;
            default:
                return mapError("Only 'include' of type PROPERTY or WRAPPER_OBJECT are supported");
        }

        switch (use) {
            case "CLASS":
            case "NAME":
                builder.member(SerdeConfig.SerSubtyped.DISCRIMINATOR_VALUE, use);
                property = property != null ? property : use.equals("CLASS") ? "@class" : "@type";
                builder.member(SerdeConfig.SerSubtyped.DISCRIMINATOR_PROP, property);
            break;
            default:
                return mapError("Unsupported JsonTypeInfo use: " + use);
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
