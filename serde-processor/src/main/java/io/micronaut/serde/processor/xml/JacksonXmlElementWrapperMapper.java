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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.config.annotation.SerdeConfig;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

/**
 * Maps Jackson XML's {@code tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper}.
 *
 * @since 3.0.0
 */
public class JacksonXmlElementWrapperMapper implements NamedAnnotationMapper {

    static final String XML_WRAPPER_PROPERTY_SERDE_CLASS = "io.micronaut.serde.xml.serde.XmlWrapperSerde";
    private static final AnnotationClassValue<?> XML_WRAPPER_PROPERTY_SERDE_CLASS_VALUE =
        new AnnotationClassValue<>(XML_WRAPPER_PROPERTY_SERDE_CLASS);

    @Override
    public @NonNull String getName() {
        return "tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper";
    }

    /**
     * Maps {@code @JacksonXmlElementWrapper} to {@link SerdeConfig}, enabling collection wrapping {@code useWrapping}
     * and applying any custom wrapper {@code localName}.
     *
     * @param annotation     The {@code @JacksonXmlElementWrapper} annotation values
     * @param visitorContext The context that is being visited
     * @return A singleton list containing the resulting {@link SerdeConfig} annotation
     */
    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        AnnotationValueBuilder<SerdeConfig> builder = AnnotationValue.builder(SerdeConfig.class);
        annotation.stringValue("useWrapping").ifPresentOrElse(useWrapping -> {
                boolean flag = Boolean.parseBoolean(useWrapping);
                builder.member(SerdeConfig.META_ANNOTATION_PROPERTY, flag);
                configureWrapperSerde(builder);
            },
            () -> {
                annotation.stringValue("localName")
                    .filter(localName -> !localName.isEmpty())
                    .ifPresent(localName -> {
                        builder.member(SerdeConfig.META_ANNOTATION_PROPERTY, true);
                        configureWrapperSerde(builder);

                    });
            }
            );

        annotation.stringValue("localName")
            .filter(localName -> !localName.isEmpty())
            .ifPresent(localName -> {
                builder.member(SerdeConfig.WRAPPER_PROPERTY,  localName);
                builder.member(SerdeConfig.ALIASES, new String[] { localName });
            });

        return Collections.singletonList(builder.build());
    }

    /**
     * Registers {@code XmlWrapperSerde} as both the serializer and deserializer so the wrapper
     * element is written and read.
     *
     * @param builder The {@link SerdeConfig} builder to configure
     */
    private static void configureWrapperSerde(AnnotationValueBuilder<SerdeConfig> builder) {
        builder.member(SerdeConfig.SERIALIZER_CLASS, XML_WRAPPER_PROPERTY_SERDE_CLASS_VALUE);
        builder.member(SerdeConfig.DESERIALIZER_CLASS, XML_WRAPPER_PROPERTY_SERDE_CLASS_VALUE);
    }
}
