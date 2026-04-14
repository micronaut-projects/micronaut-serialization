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
package io.micronaut.serde.processor.xml;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.config.annotation.SerdeConfig;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

public class JacksonXmlElementWrapperMapper implements NamedAnnotationMapper {

    @Override
    public @NonNull String getName() {
        return "tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper";
    }
    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        System.out.println("JacksonXmlElementWrapperMapper.map");
        final List<AnnotationValue<?>> MAPPED = Collections.singletonList(
            AnnotationValue.builder(SerdeConfig.class)
                .member(SerdeConfig.ARRAY_WRAPPER_PROPERTY, annotation.stringValue("localName").orElse(""))
                //.member(SerdeConfig.XML_USE_WRAPPING, annotation.booleanValue("useWrapping").orElse(true))
                .build());
        return MAPPED;

    }
}
