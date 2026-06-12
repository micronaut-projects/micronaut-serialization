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
package io.micronaut.serde.processor.jsonb;

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.inject.annotation.NamedAnnotationTransformer;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.config.annotation.SerdeConfig;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Maps {@code @JsonbTypeSerializer} to Micronaut Serialization custom serializer metadata.
 */
public final class JsonbTypeSerializerTransformer implements NamedAnnotationTransformer {
    static final String JSONB_TYPE_SERIALIZER_BRIDGE = "io.micronaut.serde.jsonb.JsonbTypeSerializerBridge";

    @Override
    public List<AnnotationValue<?>> transform(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        AnnotationClassValue<?> serializer = annotation.annotationClassValue("value").orElse(null);
        AnnotationValueBuilder<SerdeConfig> serdeConfig = AnnotationValue.builder(SerdeConfig.class)
            .member(SerdeConfig.SERIALIZER_CLASS, new AnnotationClassValue<>(JSONB_TYPE_SERIALIZER_BRIDGE));
        AnnotationValueBuilder<?> jsonbConfig = AnnotationValue.builder(JsonbTypeAdapterTransformer.JSONB_SERDE_CONFIG);
        if (serializer != null) {
            jsonbConfig.member("serializer", serializer);
        }
        return List.of(serdeConfig.build(), jsonbConfig.build());
    }

    @Override
    public String getName() {
        return "jakarta.json.bind.annotation.JsonbTypeSerializer";
    }
}
