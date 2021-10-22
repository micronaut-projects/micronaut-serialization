package io.micronaut.serde.processor.jackson;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.annotation.TypedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.annotation.SerdeConfig;

public class JsonPropertyMapper implements TypedAnnotationMapper<JsonProperty> {
    @Override
    public Class<JsonProperty> annotationType() {
        return JsonProperty.class;
    }

    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<JsonProperty> annotation, VisitorContext visitorContext) {
        final AnnotationValueBuilder<SerdeConfig> builder = AnnotationValue.builder(SerdeConfig.class);
        annotation.stringValue().ifPresent(s -> builder.member(SerdeConfig.PROPERTY, s));
        ArrayList<AnnotationValue<?>> values = new ArrayList<>();

        annotation.stringValue("defaultValue")
                .ifPresent(s ->
                    values.add(AnnotationValue.builder(Bindable.class)
                                       .member("defaultValue", s).build())
                );
        final JsonProperty.Access access = annotation.enumValue("access", JsonProperty.Access.class).orElse(null);
        if (access != null) {
            switch (access) {
                case READ_ONLY:
                    builder.member("readOnly", true);
                break;
                case WRITE_ONLY:
                    builder.member("writeOnly", true);
                break;
            }
        }
        values.add(builder.build());
        return values;
    }
}
