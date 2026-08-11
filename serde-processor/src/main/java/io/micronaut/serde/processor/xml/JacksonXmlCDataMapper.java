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
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.config.annotation.SerdeConfig;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

/**
 * Maps Jackson XML's {@code tools.jackson.dataformat.xml.annotation.JacksonXmlCData}.
 *
 * @since 3.2
 */
public final class JacksonXmlCDataMapper implements NamedAnnotationMapper {

    @Override
    public String getName() {
        return "tools.jackson.dataformat.xml.annotation.JacksonXmlCData";
    }

    /**
     * Maps an enabled {@code @JacksonXmlCData} annotation to {@link SerdeConfig}.
     *
     * @param annotation The {@code @JacksonXmlCData} annotation values
     * @param visitorContext The context that is being visited
     * @return A singleton list containing the resulting {@link SerdeConfig} annotation
     */
    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        AnnotationValueBuilder<SerdeConfig> builder = AnnotationValue.builder(SerdeConfig.class)
            .member(SerdeConfig.XML_CDATA_PROPERTY, annotation.booleanValue().orElse(true));
        return Collections.singletonList(builder.build());
    }
}
