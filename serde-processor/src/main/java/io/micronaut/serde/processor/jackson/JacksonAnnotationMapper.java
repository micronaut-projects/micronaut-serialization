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
package io.micronaut.serde.processor.jackson;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.io.service.ServiceDefinition;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder;
import io.micronaut.inject.annotation.AnnotationMapper;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.config.annotation.SerdeConfig;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Maps the {@code com.fasterxml.jackson.annotation.JacksonAnnotation} stereotype to {@link SerdeConfig}.
 */
public final class JacksonAnnotationMapper implements NamedAnnotationMapper {
    static final Map<String, List<AnnotationMapper<?>>> JACKSON_ANNOTATION_MAPPERS = new HashMap<>(10);

    static {
        SoftServiceLoader<AnnotationMapper> serviceLoader = SoftServiceLoader.load(AnnotationMapper.class,
                                                                                   AbstractAnnotationMetadataBuilder.class.getClassLoader());
        for (ServiceDefinition<AnnotationMapper> definition : serviceLoader) {
            if (definition.isPresent() && definition.getName().startsWith("io.micronaut.serde.processor.jackson.")) {
                AnnotationMapper<?> mapper = definition.load();
                try {
                    String name = null;
                    if (mapper instanceof TypedAnnotationMapper<?> typedAnnotationMapper) {
                        name = typedAnnotationMapper.annotationType().getName();
                    } else if (mapper instanceof NamedAnnotationMapper namedAnnotationMapper) {
                        name = namedAnnotationMapper.getName();
                    }
                    if (StringUtils.isNotEmpty(name)) {
                        JACKSON_ANNOTATION_MAPPERS.computeIfAbsent(name, s -> new ArrayList<>(2)).add(mapper);
                    }
                } catch (Throwable e) {
                    // mapper, missing dependencies, continue
                }
            }
        }
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        return Collections.singletonList(AnnotationValue.builder(SerdeConfig.class).build());
    }

    @Override
    public String getName() {
        return "com.fasterxml.jackson.annotation.JacksonAnnotation";
    }
}
