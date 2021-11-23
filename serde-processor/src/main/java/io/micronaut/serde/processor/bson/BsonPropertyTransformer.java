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
package io.micronaut.serde.processor.bson;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.inject.annotation.NamedAnnotationTransformer;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.config.annotation.SerdeConfig;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

/**
 * Mapper of @BsonProperty.
 *
 * @author Denis Stepanov
 */
public class BsonPropertyTransformer implements NamedAnnotationTransformer {

    private static final String BSON_DEFAULT_DISCRIMINATOR_PROPERTY_NAME = "_t";

    @Override
    public String getName() {
        return "org.bson.codecs.pojo.annotations.BsonProperty";
    }

    @Override
    public List<AnnotationValue<?>> transform(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        final AnnotationValueBuilder<SerdeConfig> builder = AnnotationValue.builder(SerdeConfig.class);
        annotation.stringValue().ifPresent(s -> {
            builder.member(SerdeConfig.PROPERTY, s);
        });
        if (annotation.booleanValue("useDiscriminator").orElse(false)) {
            builder.member(SerdeConfig.TYPE_NAME, SerdeConfig.TYPE_NAME_CLASS_SIMPLE_NAME_PLACEHOLDER);
            builder.member(SerdeConfig.TYPE_PROPERTY, BSON_DEFAULT_DISCRIMINATOR_PROPERTY_NAME);
        }
        return Collections.singletonList(builder.build());
    }
}
