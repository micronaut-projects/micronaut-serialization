package io.micronaut.serde.processor.jackson;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.annotation.SerdeConfig;

public class JsonIncludeMapper implements TypedAnnotationMapper<JsonInclude> {
    @Override
    public Class<JsonInclude> annotationType() {
        return JsonInclude.class;
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<JsonInclude> annotation, VisitorContext visitorContext) {
        final SerdeConfig.Include v = annotation.enumValue(SerdeConfig.Include.class).orElse(null);
        if (annotation.contains("valueFilter")) {
            // fail with unsupported API
        }
        if (annotation.contains("contentFilter")) {
            // fail with unsupported API
        }
        if (v != null) {
            return Collections.singletonList(
                    AnnotationValue.builder(SerdeConfig.class)
                            .member(SerdeConfig.INCLUDE, v)
                            .build()
            );
        }
        return Collections.emptyList();
    }
}
