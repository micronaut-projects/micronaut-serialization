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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.config.annotation.SerdeConfig;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Support JsonSubTypes.
 */
public class JsonSubTypesMapper implements TypedAnnotationMapper<JsonSubTypes> {
    @Override
    public Class<JsonSubTypes> annotationType() {
        return JsonSubTypes.class;
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<JsonSubTypes> annotation, VisitorContext visitorContext) {
        List<AnnotationValue<SerdeConfig.Subtyped.Subtype>> subtypes = new ArrayList<>();
        List<AnnotationValue<Annotation>> annotations = annotation.getAnnotations(AnnotationMetadata.VALUE_MEMBER);
        for (AnnotationValue<Annotation> annotationValue : annotations) {
            AnnotationClassValue<?> acv = annotationValue.annotationClassValue(AnnotationMetadata.VALUE_MEMBER).orElse(null);
            if (acv != null) {
                AnnotationValueBuilder<SerdeConfig.Subtyped.Subtype> builder = AnnotationValue.builder(SerdeConfig.Subtyped.Subtype.class)
                        .member(AnnotationMetadata.VALUE_MEMBER, acv);
                String[] names = annotationValue.stringValues("names");
                String v = annotationValue.stringValue("name").orElse(null);
                if (v != null) {
                    names = ArrayUtils.concat(names, v);
                }
                if (ArrayUtils.isNotEmpty(names)) {
                    builder.member("names", names);
                }
                subtypes.add(builder.build());
            }
        }
        if (CollectionUtils.isNotEmpty(subtypes)) {
            return Collections.singletonList(
                    AnnotationValue.builder(SerdeConfig.Subtyped.class)
                            .values(subtypes.toArray(new AnnotationValue[0]))
                            .build()
            );
        }
        return Collections.emptyList();
    }
}
