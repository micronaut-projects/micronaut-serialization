/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.serde.processor.jackson.databind;

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.config.naming.*;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

/**
 * Support for databind @JsonNaming.
 */
public class JsonNamingMapper implements NamedAnnotationMapper {
    @Override
    public String getName() {
        return "com.fasterxml.jackson.databind.annotation.JsonNaming";
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        AnnotationClassValue<?> annotationClassValue = annotation.annotationClassValue(AnnotationMetadata.VALUE_MEMBER).orElse(null);
        if (annotationClassValue != null) {
            ClassElement e = visitorContext.getClassElement(annotationClassValue.getName()).orElse(null);
            if (e != null) {
                String className = e.getSimpleName();
                int i = className.indexOf('$');
                if (i > -1) {
                    className = className.substring(i + 1);
                }
                switch (className) {
                    case "LowerCamelCaseStrategy":
                        return namingStrategy(IdentityStrategy.class);
                    case "UpperCamelCaseStrategy":
                        return namingStrategy(UpperCamelCaseStrategy.class);
                    case "SnakeCaseStrategy":
                        return namingStrategy(SnakeCaseStrategy.class);
                    case "LowerCaseStrategy":
                        return namingStrategy(LowerCaseStrategy.class);
                    case "KebabCaseStrategy":
                        return namingStrategy(KebabCaseStrategy.class);
                    case "LowerDotCaseStrategy":
                        return namingStrategy(LowerDotCaseStrategy.class);
                    default:
                        return unsupported(className);
                }
            }
        }
        return Collections.emptyList();
    }

    private List<AnnotationValue<?>> unsupported(String className) {
        return Collections.singletonList(
                AnnotationValue.builder(SerdeConfig.SerError.class)
                        .value("Unsupported Databind naming strategy: " + className)
                        .build()
        );
    }

    private List<AnnotationValue<?>> namingStrategy(Class<? extends PropertyNamingStrategy> strategy) {
        return Collections.singletonList(
                AnnotationValue.builder(SerdeConfig.class)
                        .member(SerdeConfig.NAMING, new AnnotationClassValue<>(strategy))
                        .build()
        );
    }
}
