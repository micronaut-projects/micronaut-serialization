package io.micronaut.serde.processor.jackson;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.annotation.SerdeConfig;

public class JsonIgnoreMapper implements TypedAnnotationMapper<JsonIgnore> {
    @Override
    public Class<JsonIgnore> annotationType() {
        return JsonIgnore.class;
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<JsonIgnore> annotation, VisitorContext visitorContext) {
        return Collections.singletonList(
                AnnotationValue.builder(SerdeConfig.class)
                        .member(SerdeConfig.IGNORED, true)
                        .build()
        );
    }
}
