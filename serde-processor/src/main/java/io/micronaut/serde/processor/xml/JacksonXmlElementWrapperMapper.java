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
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.config.annotation.SerdeConfig;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

/**
 *
 */
public class JacksonXmlElementWrapperMapper implements NamedAnnotationMapper {

    static final String XML_WRAPPER_PROPERTY_SERDE_CLASS = "io.micronaut.serde.xml.serde.XmlWrapperSerde";

    @Override
    public @NonNull String getName() {
        return "tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        AnnotationValueBuilder<SerdeConfig> builder = AnnotationValue.builder(SerdeConfig.class);
        annotation.stringValue("useWrapping").ifPresentOrElse(useWrapping -> {
                boolean flag = Boolean.parseBoolean(useWrapping);
                builder.member(SerdeConfig.META_ANNOTATION_PROPERTY, flag);
                // behavior not desired ~ L55-57
                // getting                    <tata312><name>hamza</name></tata312>
                builder.member(SerdeConfig.SERIALIZER_CLASS, XML_WRAPPER_PROPERTY_SERDE_CLASS);
                if (!flag) {    // @JacksonXmlElementWrapper(localName = "kilo", usewrapping="false")
                    annotation.stringValue("localName")
                        .filter(localName -> !localName.isEmpty())
                        .ifPresent(localName -> {
                            builder.member(SerdeConfig.PROPERTY, localName);
                        });
                }
            },
            () -> {
                annotation.stringValue("localName")
                    .filter(localName -> !localName.isEmpty())
                    .ifPresent(localName -> {
                        builder.member(SerdeConfig.META_ANNOTATION_PROPERTY, true);
                        builder.member(SerdeConfig.SERIALIZER_CLASS, XML_WRAPPER_PROPERTY_SERDE_CLASS);

                    });
            }
            );

        annotation.stringValue("localName")
            .filter(localName -> !localName.isEmpty())
            .ifPresent(localName -> {
                builder.member(SerdeConfig.WRAPPER_PROPERTY,  localName);
            });

        return Collections.singletonList(builder.build());
    }
}
