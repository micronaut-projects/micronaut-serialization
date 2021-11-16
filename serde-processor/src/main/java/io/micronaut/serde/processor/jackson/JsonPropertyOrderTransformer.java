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

import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.annotation.SerdeConfig;

/**
 * Support for @JsonPropertyOrder.
 */
public class JsonPropertyOrderTransformer extends ValidatingAnnotationTransformer<JsonPropertyOrder> {
    @Override
    public Class<JsonPropertyOrder> annotationType() {
        return JsonPropertyOrder.class;
    }

    @Override
    protected Set<String> getSupportedMemberNames() {
        return Collections.singleton(AnnotationMetadata.VALUE_MEMBER);
    }

    @Override
    protected List<AnnotationValue<?>> transformValid(AnnotationValue<JsonPropertyOrder> annotation,
                                                      VisitorContext visitorContext) {
        return Collections.singletonList(
                AnnotationValue.builder(SerdeConfig.PropertyOrder.class)
                        .member(AnnotationMetadata.VALUE_MEMBER, annotation.stringValues())
                        .build()
        );
    }
}
