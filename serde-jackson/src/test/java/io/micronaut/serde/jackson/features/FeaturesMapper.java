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
package io.micronaut.serde.jackson.features;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.config.annotation.SerdeConfig;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Maps {@link Features} to the format features of {@link SerdeConfig}, as {@code JsonFormatMapper} maps the
 * {@code with} and {@code without} members of {@code @JsonFormat}.
 */
public final class FeaturesMapper implements TypedAnnotationMapper<Features> {

    @Override
    public Class<Features> annotationType() {
        return Features.class;
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Features> annotation, VisitorContext visitorContext) {
        AnnotationValueBuilder<SerdeConfig> builder = AnnotationValue.builder(SerdeConfig.class);
        String[] with = annotation.stringValues("with");
        if (with.length > 0) {
            builder.member(SerdeConfig.FEATURES_WITH, with);
        }
        String[] without = annotation.stringValues("without");
        if (without.length > 0) {
            builder.member(SerdeConfig.FEATURES_WITHOUT, without);
        }
        List<AnnotationValue<? extends Annotation>> mapped = List.of(builder.build());
        return (List) mapped;
    }
}
