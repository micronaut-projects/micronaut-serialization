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
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.config.annotation.SerdeConfig;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps the {@code com.fasterxml.jackson.annotation.JsonProperty} annotation to {@link SerdeConfig}.
 */
public class JsonPropertyMapper implements NamedAnnotationMapper {

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        final AnnotationValueBuilder<SerdeConfig> builder = AnnotationValue.builder(SerdeConfig.class);
        String propertyName = annotation.stringValue().orElse(null);
        ArrayList<AnnotationValue<?>> values = new ArrayList<>();
        if (propertyName != null) {
            builder.member(SerdeConfig.PROPERTY, propertyName);
        } else {
            values.add(AnnotationValue.builder(SerdeConfig.META_ANNOTATION_PROPERTY).build());
        }

        annotation.stringValue("defaultValue").ifPresent(s ->
                       values.add(AnnotationValue.builder(Bindable.class).member("defaultValue", s).build())
                );
        annotation.stringValue("access").ifPresent(access -> {
            switch (access) {
                case "READ_ONLY":
                    builder.member("readOnly", true);
                    break;
                case "WRITE_ONLY":
                    builder.member("writeOnly", true);
                    break;
                default:
                    // no-op
            }
        });
        if (annotation.booleanValue("required").orElse(false)) {
            builder.member(SerdeConfig.REQUIRED, true);
        }
        values.add(builder.build());
        return values;
    }

    @Override
    public String getName() {
        return "com.fasterxml.jackson.annotation.JsonProperty";
    }
}
