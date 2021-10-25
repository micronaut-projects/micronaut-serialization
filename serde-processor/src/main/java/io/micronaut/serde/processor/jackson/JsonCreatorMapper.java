package io.micronaut.serde.processor.jackson;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Creator;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;

public final class JsonCreatorMapper implements TypedAnnotationMapper<JsonCreator> {

    private static final List<AnnotationValue<?>> MAPPED =
            Collections.singletonList(AnnotationValue.builder(Creator.class).build());

    @Override
    public Class<JsonCreator> annotationType() {
        return JsonCreator.class;
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<JsonCreator> annotation, VisitorContext visitorContext) {
        return MAPPED;
    }
}
