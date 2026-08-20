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
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.config.annotation.SerdeConfig;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Maps {@code @XmlAccessorOrder(ALPHABETICAL)} to serde ordering metadata.
 *
 * @since 3.2
 */
public final class JaxbXmlAccessorOrderMapper implements NamedAnnotationMapper {
    @Override
    public String getName() {
        return "jakarta.xml.bind.annotation.XmlAccessorOrder";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        return annotation.stringValue("value").filter(value -> value.endsWith("ALPHABETICAL")).isPresent()
            ? List.of(AnnotationValue.builder(SerdeConfig.META_ANNOTATION_PROPERTY_ORDER).member("alphabetic", true).build()) : List.of();
    }
}
