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
import io.micronaut.core.annotation.Introspected
import io.micronaut.json.JsonMapper
import spock.lang.Specification
import io.micronaut.serde.annotation.Serdeable
import tools.jackson.dataformat.xml.annotation.*;

abstract class TestSerializationAttrSpec extends Specification{

    abstract JsonMapper getXmlMapper();

    def "NsAttrBean - attribute with namespace"() {
        given:
        def bean = new NsAttrBean()

        when:
        def xml = xmlMapper.writeValueAsString(bean)

        then:
        xml == '<NsAttrBean other="3"></NsAttrBean>'
        xml.contains('other="3"')
        //xml.contains('xmlns:ns0="http://foo"')
    }

    def "Issue19Bean - mixed attributes, namespace and root name"() {
        given:
        def bean = new Issue19Bean()

        when:
        def xml = xmlMapper.writeValueAsString(bean)

        then:
        xml == "<test id=\"abc\"></test>"
    }

    def "Jurisdiction - multiple attributes with order"() {
        given:
        def bean = new Jurisdiction()

        when:
        def xml = xmlMapper.writeValueAsString(bean)

        then:
        xml.contains('value="13"') && xml.contains('name="Foo"')
    }

    def "DynaBean - @JsonAnyGetter as elements"() {
        given:
        def bean = new DynaBean([foo: "bar", baz: "qux"])

        when:
        def xml = xmlMapper.writeValueAsString(bean)

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
        def xml = xmlMapper.writeValueAsString(bean)

        then:
        xml == "<CollectionWrapper329><elements><data>a</data><data>b</data></elements></CollectionWrapper329>"
    }

    def "StreamWrapper329 - wrapped disabled"() {
        given:
        def bean = new ListWrapper329()
        bean.setData(List.of("a", "b"))

        when:
        def xml = xmlMapper.writeValueAsString(bean)

        then:
        xml == "<ListWrapper329><elements>a</elements><elements>b</elements></ListWrapper329>"
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
        def xml = xmlMapper.writeValueAsString(bean)

        then:
        xml == "<Values>" +
                    "<type>list</type>" +
                    "<Value><vi>a</vi></Value>" +
                    "<Value><vi>b</vi></Value>" +
                "</Values>"
    }

    // ==================== Test Beans ====================

    @Serdeable
    static class NsAttrBean {
        @JsonProperty("other")
        @JacksonXmlProperty(namespace = "http://foo", isAttribute = true)
        public String attr = "3"

        String getAttr() {
            return attr
        }
    }

    @Serdeable
    @JsonRootName(value = "test")
    static class Issue19Bean {
        @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
        public boolean booleanA = true

        @JsonProperty
        @JacksonXmlProperty(isAttribute = true, namespace = "http://my.ns")
        public String id = "abc"

        boolean getBooleanA() {
            return booleanA
        }

        String getId() {
            return id
        }
    }

    @Serdeable
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

    @Serdeable
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

    @Serdeable
    static class ListWrapper329 {
        private List<String> data

        @JacksonXmlElementWrapper(localName = "elements", useWrapping = false)
        List<String> getData() {
            return data
        }

        void setData(List<String> data) {
            this.data = data
        }
    }

    @Serdeable
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

    @Serdeable
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

    @Serdeable
    static final class Values
    {
        @JacksonXmlProperty(localName = "type")
        private String type;

        @JacksonXmlElementWrapper(localName = "kilo", useWrapping = false)
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
