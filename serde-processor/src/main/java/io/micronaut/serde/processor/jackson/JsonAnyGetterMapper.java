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
import java.util.Arrays;
import java.util.List;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.config.annotation.SerdeConfig;

/**
 * Adds support for JsonAnyGetter.
 */
public class JsonAnyGetterMapper extends ValidatingAnnotationMapper {

    @Override
    protected List<AnnotationValue<?>> mapValid(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        return Arrays.asList(
                AnnotationValue.builder(Executable.class).build(),
                AnnotationValue.builder(SerdeConfig.class).build(),
                AnnotationValue.builder(SerdeConfig.SerAnyGetter.class).build()
        );
    }

    @Override
    public String getName() {
        return "com.fasterxml.jackson.annotation.JsonAnyGetter";
    }
}
