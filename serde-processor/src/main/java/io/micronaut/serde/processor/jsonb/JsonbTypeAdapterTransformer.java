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
 * Maps {@code @JsonbTypeAdapter} to Micronaut Serialization custom serde metadata.
 */
public final class JsonbTypeAdapterTransformer implements NamedAnnotationTransformer {
    static final String JSONB_SERDE_CONFIG = "io.micronaut.serde.jsonb.JsonbSerdeConfig";
    static final String JSONB_TYPE_ADAPTER_SERDE = "io.micronaut.serde.jsonb.JsonbTypeAdapterSerde";

    @Override
    public List<AnnotationValue<?>> transform(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        AnnotationClassValue<?> adapter = annotation.annotationClassValue("value").orElse(null);
        AnnotationValueBuilder<SerdeConfig> serdeConfig = AnnotationValue.builder(SerdeConfig.class)
            .member(SerdeConfig.SERIALIZER_CLASS, new AnnotationClassValue<>(JSONB_TYPE_ADAPTER_SERDE))
            .member(SerdeConfig.DESERIALIZER_CLASS, new AnnotationClassValue<>(JSONB_TYPE_ADAPTER_SERDE));
        AnnotationValueBuilder<?> jsonbConfig = AnnotationValue.builder(JSONB_SERDE_CONFIG);
        if (adapter != null) {
            jsonbConfig.member("adapter", adapter);
        }
        return List.of(serdeConfig.build(), jsonbConfig.build());
    }

    @Override
    public String getName() {
        return "jakarta.json.bind.annotation.JsonbTypeAdapter";
    }
}
