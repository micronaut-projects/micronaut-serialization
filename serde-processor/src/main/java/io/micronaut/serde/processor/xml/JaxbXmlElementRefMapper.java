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
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.config.annotation.SerdeConfig;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Maps a single class-root {@code @XmlElementRef} to unwrapped subtype metadata.
 *
 * @since 3.2
 */
public final class JaxbXmlElementRefMapper implements NamedAnnotationMapper {
    static final String JAXB_XML_ELEMENT_REF_DEFAULT = "jakarta.xml.bind.annotation.XmlElementRef$DEFAULT";

    @Override
    public String getName() {
        return "jakarta.xml.bind.annotation.XmlElementRef";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        AnnotationClassValue<?> type = annotation.annotationClassValue("type")
            .filter(value -> !"jakarta.xml.bind.JAXBElement".equals(value.getName())
                && !JAXB_XML_ELEMENT_REF_DEFAULT.equals(value.getName()))
            .orElse(null);
        if (type == null) {
            return List.of();
        }
        AnnotationValueBuilder<SerdeConfig.SerSubtyped.SerSubtype> subtype = AnnotationValue.builder(SerdeConfig.SerSubtyped.SerSubtype.class)
            .member(AnnotationMetadata.VALUE_MEMBER, type);
        annotation.stringValue("name").filter(name -> !JaxbXmlRootElementMapper.DEFAULT.equals(name))
            .ifPresent(name -> subtype.member("names", new String[] {name}));
        return List.of(
            AnnotationValue.builder(SerdeConfig.SerUnwrapped.class).build(),
            AnnotationValue.builder(SerdeConfig.SerSubtyped.class)
                .values(subtype.build())
                .member(SerdeConfig.SerSubtyped.DISCRIMINATOR_TYPE, SerdeConfig.SerSubtyped.DiscriminatorType.WRAPPER_OBJECT)
                .member(SerdeConfig.SerSubtyped.DISCRIMINATOR_VALUE, SerdeConfig.SerSubtyped.DiscriminatorValueKind.NAME)
                .build()
        );
    }
}
