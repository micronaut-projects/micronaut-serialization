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

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.config.annotation.SerdeConfig;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Support for JsonIgnoreProperties.
 */
public class JacksonIgnorePropertiesMapper implements NamedAnnotationMapper {
    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        return List.of(
            AnnotationValue.builder(SerdeConfig.SerIgnored.class)
                .member(SerdeConfig.SerIgnored.IGNORE_UNKNOWN, annotation.booleanValue(SerdeConfig.SerIgnored.IGNORE_UNKNOWN).orElse(false))
                .member(SerdeConfig.SerIgnored.ALLOW_DESERIALIZE, annotation.booleanValue("allowSetters").orElse(false))
                .member(SerdeConfig.SerIgnored.ALLOW_SERIALIZE, annotation.booleanValue("allowGetters").orElse(false))
                .members(annotation.getValues())
                .build()
        );
    }

    @Override
    public String getName() {
        return "com.fasterxml.jackson.annotation.JsonIgnoreProperties";
    }
}
