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

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.config.annotation.SerdeConfig;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps JAXB's {@code @XmlSeeAlso} types to serde subtype metadata.
 *
 * @since 3.2
 */
public final class JaxbXmlSeeAlsoMapper implements NamedAnnotationMapper {
    @Override
    public String getName() {
        return "jakarta.xml.bind.annotation.XmlSeeAlso";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        List<AnnotationValue<SerdeConfig.SerSubtyped.SerSubtype>> subtypes = new ArrayList<>();
        for (AnnotationClassValue<?> type : annotation.annotationClassValues(AnnotationMetadata.VALUE_MEMBER)) {
            AnnotationValueBuilder<SerdeConfig.SerSubtyped.SerSubtype> subtype = AnnotationValue.builder(SerdeConfig.SerSubtyped.SerSubtype.class)
                .member(AnnotationMetadata.VALUE_MEMBER, type);
            subtypes.add(subtype.build());
        }
        if (CollectionUtils.isEmpty(subtypes)) {
            return List.of();
        }
        return List.of(
            AnnotationValue.builder(SerdeConfig.class).build(),
            AnnotationValue.builder(SerdeConfig.SerSubtyped.class)
                .values(subtypes.toArray(new AnnotationValue[0]))
                .member(SerdeConfig.SerSubtyped.DISCRIMINATOR_VALUE, SerdeConfig.SerSubtyped.DiscriminatorValueKind.DEDUCTION)
                .build()
        );
    }
}
