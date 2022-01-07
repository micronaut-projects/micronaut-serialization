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

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.annotation.AnnotationMapper;
import io.micronaut.inject.annotation.PackageRenameRemapper;
import io.micronaut.inject.visitor.VisitorContext;

/**
 * Remapper to handle AWS re-packaging of annotations.
 *
 * @author graemerocher
 */
public class AwsRemapper implements PackageRenameRemapper {
    @Override
    public String getPackageName() {
        return "com.amazonaws.lambda.thirdparty.com.fasterxml.jackson.annotation";
    }

    @Override
    public String getTargetPackage() {
        return "com.fasterxml.jackson.annotation";
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<AnnotationValue<?>> remap(AnnotationValue<?> annotation, VisitorContext visitorContext) {
        final List<AnnotationValue<?>> remapped = PackageRenameRemapper.super.remap(annotation, visitorContext);

        return (List<AnnotationValue<?>>) remapped.stream().flatMap(av -> {
            final List<AnnotationMapper<?>> annotationMappers =
                    JacksonAnnotationMapper.JACKSON_ANNOTATION_MAPPERS.get(av.getAnnotationName());
            if (CollectionUtils.isNotEmpty(annotationMappers)) {
                return annotationMappers
                        .stream().flatMap(annotationMapper -> ((AnnotationMapper) annotationMapper).map(annotation, visitorContext).stream());
            }
            return Stream.of(av);
        }).collect(Collectors.toList());
    }
}
