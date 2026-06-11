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
package io.micronaut.serde.processor.jsonb;

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.config.annotation.SerdeConfig;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Maps {@code @JsonbTypeInfo} to Micronaut Serialization subtype metadata.
 */
public final class JsonbTypeInfoMapper implements NamedAnnotationMapper {
    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        List<AnnotationValue<SerdeConfig.SerSubtyped.SerSubtype>> subtypes = new ArrayList<>();
        List<AnnotationValue<Annotation>> annotations = annotation.getAnnotations(AnnotationMetadata.VALUE_MEMBER);
        for (AnnotationValue<Annotation> annotationValue : annotations) {
            AnnotationClassValue<?> type = annotationValue.annotationClassValue("type").orElse(null);
            String alias = annotationValue.stringValue("alias").orElse(null);
            if (type != null && alias != null) {
                AnnotationValueBuilder<SerdeConfig.SerSubtyped.SerSubtype> builder = AnnotationValue.builder(SerdeConfig.SerSubtyped.SerSubtype.class)
                    .member(AnnotationMetadata.VALUE_MEMBER, type)
                    .member("names", new String[]{alias});
                subtypes.add(builder.build());
            }
        }
        if (CollectionUtils.isEmpty(subtypes)) {
            return Collections.emptyList();
        }
        return Collections.singletonList(
            AnnotationValue.builder(SerdeConfig.SerSubtyped.class)
                .values(subtypes.toArray(new AnnotationValue[0]))
                .member(SerdeConfig.SerSubtyped.DISCRIMINATOR_TYPE, SerdeConfig.SerSubtyped.DiscriminatorType.PROPERTY)
                .member(SerdeConfig.SerSubtyped.DISCRIMINATOR_VALUE, SerdeConfig.SerSubtyped.DiscriminatorValueKind.NAME)
                .member(SerdeConfig.SerSubtyped.DISCRIMINATOR_PROP, annotation.stringValue("key").orElse("@type"))
                .member(SerdeConfig.SerSubtyped.JSONB_TYPE_INFO, true)
                .build()
        );
    }

    @Override
    public String getName() {
        return "jakarta.json.bind.annotation.JsonbTypeInfo";
    }
}
