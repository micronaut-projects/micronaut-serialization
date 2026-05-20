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
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.config.annotation.SerdeConfig;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

/**
 * Maps Jackson XML's {@code tools.jackson.dataformat.xml.annotation.JacksonXmlProperty}.
 *
 * @since 3.0.0
 */
public class JacksonXmlPropertyMapper implements NamedAnnotationMapper {

    static final String XML_PROPERTY_SERDE_CLASS = "io.micronaut.serde.xml.serde.XmlPropertySerde";
    static final String XML_NAMESPACED_ELEMENT_SERDE_CLASS = "io.micronaut.serde.xml.serde.XmlNamespacedElementSerde";

    @Override
    public String getName() {
        return "tools.jackson.dataformat.xml.annotation.JacksonXmlProperty";
    }

    /**
     * Maps {@code @JacksonXmlProperty} to {@link SerdeConfig}, handling attribute and element
     * representation, a custom {@code localName} and an optional XML {@code namespace}.
     *
     * @param annotation     The {@code @JacksonXmlProperty} annotation values
     * @param visitorContext The context that is being visited
     * @return A singleton list containing the resulting {@link SerdeConfig} annotation
     */
    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        AnnotationValueBuilder<SerdeConfig> builder = AnnotationValue.builder(SerdeConfig.class);
        boolean isAttribute = annotation.booleanValue("isAttribute")
            .orElse(false);
        if (isAttribute) {
            builder.member(SerdeConfig.XML_ATTRIBUTE_PROPERTY, true);
            builder.member(SerdeConfig.SERIALIZER_CLASS, new AnnotationClassValue<>(XML_PROPERTY_SERDE_CLASS));
            builder.member(SerdeConfig.DESERIALIZER_CLASS, new AnnotationClassValue<>(XML_PROPERTY_SERDE_CLASS));
        }
        annotation.stringValue("localName")
            .filter(localName -> !localName.isEmpty())
            .ifPresent(localName -> builder.member(SerdeConfig.PROPERTY, localName));
        annotation.stringValue("namespace")
            .filter(ns -> !ns.isEmpty())
            .ifPresent(ns -> {
                builder.member(SerdeConfig.XML_NAMESPACE, ns);
                if (!isAttribute) {
                    builder.member(SerdeConfig.SERIALIZER_CLASS, new AnnotationClassValue<>(XML_NAMESPACED_ELEMENT_SERDE_CLASS));
                }
            });
        return Collections.singletonList(builder.build());
    }
}
