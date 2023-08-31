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
package io.micronaut.serde.processor.jackson.databind;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.micronaut.core.annotation.AccessorsStyle;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.processor.jackson.ValidatingAnnotationMapper;

/**
 * Support for JsonDeserialize(as=MyType).
 */
public class JsonDeserializeMapper extends ValidatingAnnotationMapper {
    @Override
    protected List<AnnotationValue<?>> mapValid(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        AnnotationClassValue<?> acv = annotation.annotationClassValue("as").orElse(null);
        List<AnnotationValue<?>> annotations = new ArrayList<>();
        if (acv != null) {
            annotations.add(
                    AnnotationValue.builder(SerdeConfig.class)
                            .member(SerdeConfig.DESERIALIZE_AS, acv)
                            .build()
            );
        }
        AnnotationClassValue<?> builderClass = annotation.annotationClassValue("builder").orElse(null);
        if (builderClass != null) {
            AnnotationValueBuilder<Introspected.IntrospectionBuilder> builderDef = AnnotationValue.builder(Introspected.IntrospectionBuilder.class);
            builderDef.member("builderClass", builderClass);
            visitorContext.getClassElement(builderClass.getName()).ifPresent(t -> {
                AnnotationValue<Annotation> jsonPojoAnn = t.getAnnotation("com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder");
                if (jsonPojoAnn != null) {
                    jsonPojoAnn.stringValue("buildMethodName").ifPresent(n -> builderDef.member("creatorMethod", n));
                    jsonPojoAnn.stringValue("withPrefix").ifPresent(n ->
                        builderDef.member("accessorStyle", AnnotationValue.builder(AccessorsStyle.class).member("writePrefixes", n).build())
                    );
                }
            });
            AnnotationValueBuilder<Introspected> builder = AnnotationValue.builder(Introspected.class)
                .member("builder", builderDef.build())
                .member("builderClass", builderClass);
            annotations.add(
                builder.build()
            );
        }
        return annotations;
    }

    @Override
    protected Set<String> getSupportedMemberNames() {
        return Set.of("as", "builder");
    }

    @Override
    public String getName() {
        return "com.fasterxml.jackson.databind.annotation.JsonDeserialize";
    }
}
