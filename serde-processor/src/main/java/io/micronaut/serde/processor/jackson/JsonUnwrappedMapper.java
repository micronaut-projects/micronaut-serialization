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

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.config.annotation.SerdeConfig;

/**
 * Adds support for Jackson's JsonUnwrapped.
 */
public class JsonUnwrappedMapper implements NamedAnnotationMapper {
    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        final AnnotationValueBuilder<SerdeConfig.SerUnwrapped> unwrapped =
                AnnotationValue.builder(SerdeConfig.SerUnwrapped.class);
        annotation.stringValue(SerdeConfig.SerUnwrapped.PREFIX).ifPresent(v -> unwrapped.member(
                SerdeConfig.SerUnwrapped.PREFIX, v
        ));
        annotation.stringValue(SerdeConfig.SerUnwrapped.SUFFIX).ifPresent(v -> unwrapped.member(
                SerdeConfig.SerUnwrapped.SUFFIX, v
        ));
        return Collections.singletonList(unwrapped.build());
    }

    @Override
    public String getName() {
        return "com.fasterxml.jackson.annotation.JsonUnwrapped";
    }

}
