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
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.config.annotation.SerdeConfig;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Transformer for {@code com.fasterxml.jackson.annotation.JsonFormat}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class JsonFormatMapper implements NamedAnnotationMapper {
    private static final String WITH = "with";
    private static final String WITHOUT = "without";

    private static final Set<String> STRING_MEMBER_NAMES = CollectionUtils.setOf(
        SerdeConfig.PATTERN,
        SerdeConfig.SHAPE,
        SerdeConfig.LOCALE,
        SerdeConfig.TIMEZONE
    );

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        final AnnotationValueBuilder<SerdeConfig> builder = AnnotationValue.builder(SerdeConfig.class);
        for (String memberName : STRING_MEMBER_NAMES) {
            annotation.stringValue(memberName)
                .ifPresent(p -> builder.member(memberName, p));
        }
        annotation.intValue(SerdeConfig.RADIX)
            .ifPresent(p -> builder.member(SerdeConfig.RADIX, p));
        annotation.stringValue(SerdeConfig.LENIENT)
            .ifPresent(p -> {
                if ("TRUE".equalsIgnoreCase(p)) {
                    builder.member(SerdeConfig.LENIENT, true);
                } else if ("FALSE".equalsIgnoreCase(p)) {
                    builder.member(SerdeConfig.LENIENT, false);
                }
            });
        String[] with = annotation.stringValues(WITH);
        if (with.length == 0) {
            with = annotation.stringValues(SerdeConfig.FEATURES_WITH);
        }
        if (with.length > 0) {
            builder.member(SerdeConfig.FEATURES_WITH, with);
        }
        String[] without = annotation.stringValues(WITHOUT);
        if (without.length == 0) {
            without = annotation.stringValues(SerdeConfig.FEATURES_WITHOUT);
        }
        if (without.length > 0) {
            builder.member(SerdeConfig.FEATURES_WITHOUT, without);
        }

        return Collections.singletonList(builder.build());
    }

    @Override
    public String getName() {
        return "com.fasterxml.jackson.annotation.JsonFormat";
    }
}
