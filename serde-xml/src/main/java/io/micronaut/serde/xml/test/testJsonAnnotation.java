package io.micronaut.serde.xml.test;

import com.fasterxml.jackson.annotation.*;
import io.micronaut.context.ApplicationContext;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.xml.XmlObjectMapper;
import io.micronaut.serde.xml.XmlReader;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;

public class testJsonAnnotation {
    public static void main(String[] args) {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            XmlObjectMapper bean = ctx.getBean(XmlObjectMapper.class);

            //var s = bean.writeValueAsString(new Issue19Bean());
            //var s = bean.writeValueAsString(new Jurisdiction());
            var se = bean.writeValueAsString(new DynaBean(Map.of("foo", "bar", "baz", "qux")));
            var s = bean.writeValueAsString(new NsAttrBean());
            System.out.println(s);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
        @Serdeable
        static class NsAttrBean {
            //@JacksonXmlProperty(namespace = "http://foo", isAttribute = true)
            //@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
            public String attr = "3";

            public String getAttr() {
                return attr;
            }
        }

    @Serdeable
    @JsonRootName(value = "test")
    static class Issue19Bean {
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        //@JacksonXmlProperty(namespace = "http://my.ns")
        public boolean booleanA = true;

        @JsonProperty
        //@JacksonXmlProperty(isAttribute = true)
        public String id = "abc";

        public boolean isBooleanA() {
            return booleanA;
        }

        public String getId() {
            return id;
        }
    }

    @Serdeable
    @JsonPropertyOrder({"name", "value"})
    static class Jurisdiction {
        @JsonProperty
        //@JacksonXmlProperty(isAttribute = true)
        protected String name = "Foo";

        @JsonProperty
        //@JacksonXmlProperty(isAttribute = true)
        protected int value = 13;

        public String getName() {
            return name;
        }

        public int getValue() {
            return value;
        }
    }

    @Serdeable
    @JsonRootName(value = "dynaBean")
    //@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "class", include = JsonTypeInfo.As.PROPERTY)
    static class DynaBean {

        private final Map<String, String> properties = new TreeMap<>();

        DynaBean(Map<String, String> values) {
            properties.putAll(values);
        }

        @JsonAnyGetter
        //@JacksonXmlProperty(isAttribute = false)
        Map<String, String> getProperties() {
            return properties;
        }
    }
}
