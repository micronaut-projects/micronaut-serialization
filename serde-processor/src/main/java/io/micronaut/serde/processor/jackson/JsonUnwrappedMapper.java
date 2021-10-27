package io.micronaut.serde.processor.jackson;

import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.annotation.SerdeConfig;

public class JsonUnwrappedMapper implements TypedAnnotationMapper<JsonUnwrapped> {
    @Override
    public Class<JsonUnwrapped> annotationType() {
        return JsonUnwrapped.class;
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<JsonUnwrapped> annotation, VisitorContext visitorContext) {
        final AnnotationValueBuilder<SerdeConfig.Unwrapped> unwrapped =
                AnnotationValue.builder(SerdeConfig.Unwrapped.class);
        annotation.stringValue(SerdeConfig.Unwrapped.PREFIX).ifPresent(v -> unwrapped.member(
                SerdeConfig.Unwrapped.PREFIX, v
        ));
        annotation.stringValue(SerdeConfig.Unwrapped.SUFFIX).ifPresent(v -> unwrapped.member(
                SerdeConfig.Unwrapped.SUFFIX, v
        ));
        return Collections.singletonList(unwrapped.build());
    }
}
