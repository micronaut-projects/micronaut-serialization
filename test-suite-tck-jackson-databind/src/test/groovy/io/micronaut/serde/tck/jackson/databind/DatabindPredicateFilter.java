package io.micronaut.serde.tck.jackson.databind;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsonFormatVisitors.JsonObjectFormatVisitor;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.PropertyFilter;
import com.fasterxml.jackson.databind.ser.PropertyWriter;
import io.micronaut.serde.jackson.JsonFilterSpec;
import jakarta.inject.Singleton;

import java.util.function.Predicate;

@Singleton
public class DatabindPredicateFilter implements PropertyFilter, JsonFilterSpec.PredicateFilter {

    private Predicate<Object> predicate;

    @Override
    public void setPredicate(Predicate<Object> predicate) {
        this.predicate = predicate;
    }

    @Override
    public void serializeAsField(Object pojo, JsonGenerator gen, SerializerProvider prov, PropertyWriter writer) throws Exception {
        BeanPropertyWriter beanPropertyWriter = (BeanPropertyWriter) writer;
        Object value = beanPropertyWriter.get(pojo);
        if (predicate.test(value)) {
            writer.serializeAsField(pojo, gen, prov);
        }
    }

    @Override
    public void serializeAsElement(Object elementValue, JsonGenerator gen, SerializerProvider prov, PropertyWriter writer) throws Exception {
        BeanPropertyWriter beanPropertyWriter = (BeanPropertyWriter) writer;
        Object value = beanPropertyWriter.get(elementValue);
        if (predicate.test(value)) {
            writer.serializeAsElement(elementValue, gen, prov);
        }
    }

    @Override
    public void depositSchemaProperty(PropertyWriter writer, ObjectNode propertiesNode, SerializerProvider provider) throws JsonMappingException {
        writer.depositSchemaProperty(propertiesNode, provider);
    }

    @Override
    public void depositSchemaProperty(PropertyWriter writer, JsonObjectFormatVisitor objectVisitor, SerializerProvider provider) throws JsonMappingException {
        writer.depositSchemaProperty(objectVisitor, provider);
    }


}
