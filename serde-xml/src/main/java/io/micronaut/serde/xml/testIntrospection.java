package io.micronaut.serde.xml;

import io.micronaut.serde.annotation.Serdeable;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;

import java.util.List;

@Serdeable
public class testIntrospection {

    String name;
    List<Object> properties;
    String value;

    public testIntrospection(String name, List<Object> properties, String value) {
        this.name = name;
        this.properties = properties;
        this.value = value;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }
    public List<Object> getProperties() {
        return properties;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
