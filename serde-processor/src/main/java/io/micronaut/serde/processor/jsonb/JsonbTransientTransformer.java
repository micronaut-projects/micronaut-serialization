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

package io.micronaut.serde.processor.jsonb;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.NamedAnnotationTransformer;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.annotation.SerdeConfig;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

/**
 *
 * @author gkrocher
 */
public class JsonbTransientTransformer implements NamedAnnotationTransformer {

    @Override
    public List<AnnotationValue<?>> transform(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        return Collections.singletonList(
            AnnotationValue.builder(SerdeConfig.class)
                .member("ignored", true)
                .build()
        );
    }

    @Override
    public String getName() {
        return "jakarta.json.bind.annotation.JsonbTransient";
    }

}
