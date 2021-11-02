package io.micronaut.serde.processor.jackson;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JacksonAnnotation;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.annotation.SerdeConfig;

public class JacksonAnnotationMapper implements TypedAnnotationMapper<JacksonAnnotation> {
    @Override
    public Class<JacksonAnnotation> annotationType() {
        return JacksonAnnotation.class;
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<JacksonAnnotation> annotation, VisitorContext visitorContext) {
        return Collections.singletonList(AnnotationValue.builder(SerdeConfig.class).build());
    }
}
