/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.serde.processor.serde;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.config.annotation.SerdeConfig;
import java.util.List;

/**
 * Maps {@link Serdeable.Serializable} members to {@link SerdeConfig} metadata.
 */
public final class SerializableMapper
    implements TypedAnnotationMapper<Serdeable.Serializable> {
    @Override
    public Class<Serdeable.Serializable> annotationType() {
        return Serdeable.Serializable.class;
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Serdeable.Serializable> annotation, VisitorContext visitorContext) {
        AnnotationValueBuilder<SerdeConfig> builder = AnnotationValue.builder(SerdeConfig.class);
        annotation.annotationClassValue("as").ifPresent(as ->
            builder
                .member(SerdeConfig.SERIALIZE_AS, as)
        );
        annotation.annotationClassValue("using").ifPresent(using ->
            builder
                .member(SerdeConfig.SERIALIZER_CLASS, using)
        );
        annotation.booleanValue("validate").ifPresent(validation ->
            builder
                .member(SerdeConfig.VALIDATE, validation)
        );
        annotation.annotationClassValue("naming").ifPresent(naming ->
            builder
                .member(SerdeConfig.NAMING, naming)
        );
        return List.of(builder.build());
    }
}
