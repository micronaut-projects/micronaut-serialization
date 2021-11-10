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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonGetter;
import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.inject.annotation.TypedAnnotationTransformer;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.annotation.SerdeConfig;

/**
 * Support for Jackson's JsonGetter.
 */
public class JsonGetterTransformer implements TypedAnnotationTransformer<JsonGetter> {
    @Override
    public Class<JsonGetter> annotationType() {
        return JsonGetter.class;
    }

    @Override
    public List<AnnotationValue<?>> transform(AnnotationValue<JsonGetter> annotation, VisitorContext visitorContext) {
        final String n = annotation.stringValue().orElse(null);
        List<AnnotationValue<?>> values = new ArrayList<>(3);
        if (n != null) {
            final AnnotationValueBuilder<SerdeConfig> av = AnnotationValue.builder(SerdeConfig.class)
                    .member(SerdeConfig.PROPERTY, n);
            values.add(av.build());
        }
        values.addAll(Arrays.asList(
                AnnotationValue.builder(Executable.class).build(),
                AnnotationValue.builder(SerdeConfig.Getter.class).build()
        ));
        return values;
    }
}
