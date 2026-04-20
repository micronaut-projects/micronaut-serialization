package io.micronaut.serde.processor.xml;

import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.inject.annotation.NamedAnnotationMapper;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.serde.config.annotation.SerdeConfig;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;

public class JacksonXmlPropertyMapper implements NamedAnnotationMapper {


    static final String XML_PROPERTY_SERDE_CLASS = "io.micronaut.serde.xml.serde.XmlSerde";


    @Override
    public String getName() {
        return "tools.jackson.dataformat.xml.annotation.JacksonXmlProperty";
    }


    @Override
    public List<AnnotationValue<?>> map(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
        AnnotationValueBuilder<SerdeConfig> builder = AnnotationValue.builder(SerdeConfig.class);
        annotation.stringValue("isAttribute").ifPresent(isAttribute -> {
            boolean flagg = Boolean.valueOf(isAttribute);
            if (flagg) {
                builder.member(SerdeConfig.SERIALIZER_CLASS, new AnnotationClassValue<>(XML_PROPERTY_SERDE_CLASS));
            }


        });
        annotation.stringValue("localName")
            .filter(localName -> !localName.isEmpty())
            .ifPresent(localName -> builder.member(SerdeConfig.PROPERTY, localName));
        return Collections.singletonList(builder.build());
    }
}
