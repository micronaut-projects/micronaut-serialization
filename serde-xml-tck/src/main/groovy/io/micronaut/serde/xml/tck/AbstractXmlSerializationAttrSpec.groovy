/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.serde.xml.tck

import com.fasterxml.jackson.annotation.*
import spock.lang.Specification
import io.micronaut.serde.annotation.SerdeableGenerated
import tools.jackson.dataformat.xml.annotation.*;

abstract class AbstractXmlSerializationAttrSpec extends Specification implements XmlSpec {

    def "NsAttrBean - attribute"() {
        given:
        def bean = new NsAttrBean()

        when:
        def xml = writeXml(bean)
        def read = readXml(xml, NsAttrBean)

        then:
        xml == '<NsAttrBean other="3"></NsAttrBean>'
        xml.contains('other="3"')
        read.attr == "3"
    }

    def "Issue19Bean - mixed attributes, and root name"() {
        given:
        def bean = new Issue19Bean()

        when:
        def xml = writeXml(bean)
        def read = readXml(xml, Issue19Bean)

        then:
        xml == "<test id=\"abc\"></test>"
        read.id == "abc"
    }

    def "Jurisdiction - multiple attributes with order"() {
        given:
        def bean = new Jurisdiction()

        when:
        def xml = writeXml(bean)
        def read = readXml(xml, Jurisdiction)

        then:
        xml.contains('value="13"') && xml.contains('name="Foo"')
        read.name == "Foo"
        read.value == 13
    }

    def "AlphabeticAttributeBean - attribute is prioritized before alphabetic order"() {
        given:
        def bean = new AlphabeticAttributeBean()

        when:
        def xml = writeXml(bean)
        def read = readXml(xml, AlphabeticAttributeBean)

        then:
        xml == '<AlphabeticAttributeBean type="demo"><alpha>value</alpha></AlphabeticAttributeBean>'
        read.alpha == "value"
        read.type == "demo"
    }

    def "OrderedAttributeBean - attribute is prioritized before explicit property order"() {
        given:
        def bean = new OrderedAttributeBean()

        when:
        def xml = writeXml(bean)
        def read = readXml(xml, OrderedAttributeBean)

        then:
        xml == '<OrderedAttributeBean type="demo"><alpha>value</alpha></OrderedAttributeBean>'
        read.alpha == "value"
        read.type == "demo"
    }

    def "RenamedAttributeBean - localName and isAttribute are both honored"() {
        given:
        def bean = new RenamedAttributeBean()

        when:
        def xml = writeXml(bean)
        def read = readXml(xml, RenamedAttributeBean)

        then:
        xml == '<RenamedAttributeBean Foo="bar"><value>baz</value></RenamedAttributeBean>'
        read.foo == "bar"
        read.value == "baz"
    }

    def "DynaBean - @JsonAnyGetter as elements"() {
        given:
        def bean = new DynaBean([foo: "bar", baz: "qux"])

        when:
        def xml = writeXml(bean)

        then:
        xml == "<Root>" +
                "<baz>qux</baz>" +
                "<foo>bar</foo>" +
                "</Root>"
    }

    def "CollectionWrapper329 - wrapped collection uses custom item name"() {
        given:
        Collection<String> collection = new ArrayList<>()
        collection.add("a")
        collection.add("b")
        def bean = new CollectionWrapper329()
        bean.setData(collection)

        when:
        def xml = writeXml(bean)
        def read = readXml(xml, CollectionWrapper329)

        then:
        xml == "<CollectionWrapper329><elements><data>a</data><data>b</data></elements></CollectionWrapper329>"
        read.data as List == ["a", "b"]
    }

    def "StreamWrapper329 - wrapped disabled"() {
        given:
        def bean = new ListWrapper329()
        bean.setData(List.of("a", "b"))

        when:
        def xml = writeXml(bean)
        def read = readXml(xml, ListWrapper329)

        then:
        xml == "<ListWrapper329><data>a</data><data>b</data></ListWrapper329>"
        read.data == ["a", "b"]
    }

    def "Values - List as Object with JacksonAnnotations"() {
        given:
        def bean = new Values()
        bean.type = "list"

        def first = new Value()
        first.v = "a"

        def second = new Value()
        second.v = "b"

        bean.values = [first, second]

        when:
        def xml = writeXml(bean)
        def read = readXml(xml, Values)

        then:
        xml == "<Values>" +
                "<type>list</type>" +
                "<kilo>" +
                "<values><vi>a</vi></values>" +
                "<values><vi>b</vi></values>" +
                "</kilo>" +
                "</Values>"
        read.type == "list"
        read.values*.v == ["a", "b"]
    }

    @SerdeableGenerated(skip = true)
    static class NsAttrBean {
        @JsonProperty("other")
        @JacksonXmlProperty(isAttribute = true)
        public String attr = "3"

        String getAttr() {
            return attr
        }
    }

    @SerdeableGenerated(skip = true)
    @JsonRootName(value = "test")
    static class Issue19Bean {
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        public boolean booleanA = true

        @JsonProperty
        @JacksonXmlProperty(isAttribute = true)
        public String id = "abc"

        boolean getBooleanA() {
            return booleanA
        }

        String getId() {
            return id
        }
    }

    @SerdeableGenerated(skip = true)
    @JsonPropertyOrder(["value", "name"])
    static class Jurisdiction {
        @JacksonXmlProperty(isAttribute = true)
        protected String name = "Foo"

        @JacksonXmlProperty(isAttribute = true)
        protected int value = 13

        String getName() {
            return name
        }

        int getValue() {
            return value
        }
    }

    @SerdeableGenerated(skip = true)
    @JsonPropertyOrder(alphabetic = true)
    static class AlphabeticAttributeBean {
        private String alpha = "value"

        @JacksonXmlProperty(isAttribute = true)
        private String type = "demo"

        String getAlpha() {
            return alpha
        }

        void setAlpha(String alpha) {
            this.alpha = alpha
        }

        String getType() {
            return type
        }

        void setType(String type) {
            this.type = type
        }
    }

    @SerdeableGenerated(skip = true)
    @JsonPropertyOrder(["alpha", "type"])
    static class OrderedAttributeBean {
        private String alpha = "value"

        @JacksonXmlProperty(isAttribute = true)
        private String type = "demo"

        String getAlpha() {
            return alpha
        }

        void setAlpha(String alpha) {
            this.alpha = alpha
        }

        String getType() {
            return type
        }

        void setType(String type) {
            this.type = type
        }
    }

    @SerdeableGenerated(skip = true)
    static class RenamedAttributeBean {
        @JacksonXmlProperty(localName = "Foo", isAttribute = true)
        private String foo = "bar"

        private String value = "baz"

        String getFoo() {
            return foo
        }

        void setFoo(String foo) {
            this.foo = foo
        }

        String getValue() {
            return value
        }

        void setValue(String value) {
            this.value = value
        }
    }

    @SerdeableGenerated(skip = true)
    @JsonRootName(value = "Root")
    static class DynaBean {
        private final Map<String, String> properties = new TreeMap<>()

        DynaBean(Map<String, String> values) {
            properties.putAll(values)
        }

        @JsonAnyGetter
        @JacksonXmlProperty(isAttribute = false)
        Map<String, String> getProperties() {
            properties
        }

    }

    @SerdeableGenerated(skip = true)
    static class ListWrapper329 {
        @JacksonXmlElementWrapper(localName = "elements", useWrapping = false)
        private List<String> data


        List<String> getData() {
            return data
        }

        void setData(List<String> data) {
            this.data = data
        }
    }

    @SerdeableGenerated(skip = true)
    static class CollectionWrapper329 {
        private Collection<String> data

        @JacksonXmlElementWrapper(localName = "elements")
        Collection<String> getData() {
            return data
        }

        void setData(Collection<String> data) {
            this.data = data
        }
    }

    @SerdeableGenerated(skip = true)
    static final class Value {
        @JacksonXmlProperty(localName = "vi")
        public String v;

        Value(String v) {
            this.v = v
        }
        Value() {
        }

        String getV() {
            return v
        }

        void setV(String v) {
            this.v = v
        }
    }

    @SerdeableGenerated(skip = true)
    static final class Values
    {
        @JacksonXmlProperty(localName = "type")
        private String type;

        @JacksonXmlElementWrapper(localName = "kilo", useWrapping = true)
        List<Value> values = new ArrayList<Value>();

        Values(String type, List<Value> values) {
            this.type = type
            this.values = values
        }

        Values() {
        }

        String getType() {
            return type
        }

        void setType(String type) {
            this.type = type
        }

        List<Value> getValues() {
            return values
        }

        void setValues(List<Value> values) {
            this.values = values
        }
    }
}
