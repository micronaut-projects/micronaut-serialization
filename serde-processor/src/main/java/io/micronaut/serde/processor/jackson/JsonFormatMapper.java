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

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.config.annotation.SerdeConfig;

/**
 * Transformer for {@link com.fasterxml.jackson.annotation.JsonFormat}.
 *
 * @author graemerocher
 * @since 1.0.0
 */
public class JsonFormatMapper extends ValidatingAnnotationMapper {

    private static final Set<String> MEMBER_NAMES = CollectionUtils.setOf(
            SerdeConfig.PATTERN,
            SerdeConfig.LOCALE,
            SerdeConfig.TIMEZONE,
            SerdeConfig.LENIENT
    );

    @Override
    protected List<AnnotationValue<?>> mapValid(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        final AnnotationValueBuilder<SerdeConfig> builder = AnnotationValue.builder(SerdeConfig.class);
        for (String memberName : MEMBER_NAMES) {
            annotation.stringValue(memberName)
                    .ifPresent(p -> builder.member(memberName, p));
        }

        return Collections.singletonList(builder.build());
    }

    @Override
    protected Set<String> getSupportedMemberNames() {
        return MEMBER_NAMES;
    }

    @Override
    public String getName() {
        return "com.fasterxml.jackson.annotation.JsonFormat";
    }
}
