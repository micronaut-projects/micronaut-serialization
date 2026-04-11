package io.micronaut.serde.processor.xml;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.config.annotation.SerdeConfig;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

public class JacksonXmlElementWrapperMapper implements NamedAnnotationMapper {

    @Override
    public @NonNull String getName() {
        return "tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper";
    }
    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        System.out.println("JacksonXmlElementWrapperMapper.map");
        final List<AnnotationValue<?>> MAPPED = Collections.singletonList(
            AnnotationValue.builder(SerdeConfig.class)
                .member(SerdeConfig.ARRAY_WRAPPER_PROPERTY, annotation.stringValue("localName").orElse(""))
                //.member(SerdeConfig.XML_USE_WRAPPING, annotation.booleanValue("useWrapping").orElse(true))
                .build());
        return MAPPED;

    }
}
