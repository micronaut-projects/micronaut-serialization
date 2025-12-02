package io.micronaut.serde.tck.jackson.databind;

import io.micronaut.serde.jackson.JsonFilterSpec;
import jakarta.inject.Singleton;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.PropertyFilter;
import tools.jackson.databind.ser.PropertyWriter;

import java.util.function.Predicate;

@Singleton
public class DatabindPredicateFilter implements PropertyFilter, JsonFilterSpec.PredicateFilter {

    private Predicate<Object> predicate;

    @Override
    public void setPredicate(Predicate<Object> predicate) {
        this.predicate = predicate;
    }

    @Override
    public void serializeAsProperty(Object pojo, JsonGenerator g, SerializationContext ctxt, PropertyWriter writer) throws Exception {
        if (writer instanceof BeanPropertyWriter beanPropertyWriter) {
            Object value = beanPropertyWriter.get(pojo);
            if (predicate.test(value)) {
                beanPropertyWriter.serializeAsProperty(pojo, g, ctxt);
            }
        }
    }

    @Override
    public void serializeAsElement(Object elementValue, JsonGenerator gen, SerializationContext ctxt, PropertyWriter writer) throws Exception {
        BeanPropertyWriter beanPropertyWriter = (BeanPropertyWriter) writer;
        Object value = beanPropertyWriter.get(elementValue);
        if (predicate.test(value)) {
            writer.serializeAsElement(elementValue, gen, ctxt);
        }
    }

    @Override
    public void depositSchemaProperty(PropertyWriter writer, JsonObjectFormatVisitor v, SerializationContext ctxt) {
        writer.depositSchemaProperty(v, ctxt);
    }

    @Override
    public PropertyFilter snapshot() {
        return null;
    }
}
