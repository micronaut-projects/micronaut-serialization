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
import java.util.List;

/**
 * Maps {@code @XmlElementWrapper} to XML serde metadata.
 *
 * @since 3.2
 */
public final class JaxbXmlElementWrapperMapper implements NamedAnnotationMapper {
    @Override
    public String getName() {
        return "jakarta.xml.bind.annotation.XmlElementWrapper";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        AnnotationValueBuilder<SerdeConfig> builder = AnnotationValue.builder(SerdeConfig.class)
            .member(SerdeConfig.META_ANNOTATION_PROPERTY, true);
        annotation.stringValue("name").filter(name -> !JaxbXmlRootElementMapper.DEFAULT.equals(name)).ifPresent(name -> {
            builder.member(SerdeConfig.WRAPPER_PROPERTY, name);
            builder.member(SerdeConfig.ALIASES, new String[] {name});
        });
        annotation.stringValue("namespace").filter(namespace -> !JaxbXmlRootElementMapper.DEFAULT.equals(namespace)).ifPresent(namespace -> builder.member(SerdeConfig.XML_WRAPPER_NAMESPACE, namespace));
        return List.of(builder.build());
    }
}
