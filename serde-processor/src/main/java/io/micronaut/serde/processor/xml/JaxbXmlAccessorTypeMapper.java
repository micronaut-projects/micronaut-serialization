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
import io.micronaut.core.annotation.Introspected;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.util.List;

/**
 * Maps JAXB {@code @XmlAccessorType} to the corresponding introspection access mode.
 *
 * @since 3.2
 */
public final class JaxbXmlAccessorTypeMapper implements NamedAnnotationMapper {
    @Override
    public String getName() {
        return "jakarta.xml.bind.annotation.XmlAccessorType";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        String accessType = annotation.stringValue("value").orElse("PUBLIC_MEMBER");
        return switch (accessType) {
            case "FIELD" -> List.of(introspected(Introspected.AccessKind.FIELD, Introspected.Visibility.ANY));
            case "PROPERTY", "PUBLIC_MEMBER" -> List.of(introspected(Introspected.AccessKind.METHOD, Introspected.Visibility.PUBLIC));
            case "NONE" -> List.of(AnnotationValue.builder(Introspected.class)
                .member("accessKind", Introspected.AccessKind.METHOD, Introspected.AccessKind.FIELD)
                .member("visibility", Introspected.Visibility.ANY)
                .build());
            default -> List.of();
        };
    }

    private static AnnotationValue<Introspected> introspected(Introspected.AccessKind accessKind,
                                                                Introspected.Visibility visibility) {
        return AnnotationValue.builder(Introspected.class)
            .member("accessKind", accessKind)
            .member("visibility", visibility)
            .build();
    }
}
