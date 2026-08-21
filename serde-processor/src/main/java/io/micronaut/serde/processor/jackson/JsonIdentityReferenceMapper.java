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
package io.micronaut.serde.processor.jackson;

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.config.annotation.SerdeConfig;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Maps Jackson identity references that are always written as their object ID.
 *
 * @since 3.2
 */
public final class JsonIdentityReferenceMapper implements NamedAnnotationMapper {
    private static final String SERIALIZER = "io.micronaut.serde.support.serializers.JsonIdentityReferenceSerializer";
    private static final String DESERIALIZER = "io.micronaut.serde.support.deserializers.JsonIdentityReferenceDeserializer";

    @Override
    public String getName() {
        return "com.fasterxml.jackson.annotation.JsonIdentityReference";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        if (!annotation.booleanValue("alwaysAsId").orElse(false)) {
            return List.of();
        }
        return List.of(AnnotationValue.builder(SerdeConfig.class)
            .member(SerdeConfig.SERIALIZER_CLASS, new AnnotationClassValue<>(SERIALIZER))
            .member(SerdeConfig.DESERIALIZER_CLASS, new AnnotationClassValue<>(DESERIALIZER))
            .build());
    }
}
