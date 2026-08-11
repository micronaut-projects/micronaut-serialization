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

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.fasterxml.jackson.annotation.JsonValue
import io.micronaut.core.type.Argument
import io.micronaut.serde.annotation.Serdeable
import spock.lang.Specification
import tools.jackson.dataformat.xml.annotation.JacksonXmlCData
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import tools.jackson.dataformat.xml.annotation.JacksonXmlText

/**
 * Portable scenarios adapted from the Jackson Dataformat XML 3.1 test suite.
 *
 * <p>The scenarios in this spec exercise data-binding behavior shared by Jackson XML and
 * Micronaut Serialization. Jackson-only streaming, tree-model, JAXB, schema-validation,
 * mapper-lifecycle, and pretty-printing behavior remains in Jackson's own test suite.</p>
 *
 * @since 3.2
 */
abstract class AbstractJacksonXmlParitySpec extends Specification implements XmlSpec {

    def "root numeric values round trip"() {
        when:
        def xml = writeXml(value)
        def decoded = readXml(xml, Argument.of(value.class))

        then:
        decoded == value

        where:
        value << [
            42,
            -137L,
            0.25d,
            BigInteger.valueOf(31337),
            new BigDecimal("2e308")
        ]
    }

    def "enums round trip as root values and bean properties"() {
        given:
        def bean = new EnumBean(value: TestEnum.B)

        when:
        def rootXml = writeXml(TestEnum.B)
        def beanXml = writeXml(bean)

        then:
        readXml(rootXml, TestEnum) == TestEnum.B
        readXml(beanXml, EnumBean).value == TestEnum.B
    }

    def "enum JsonValue and delegating JsonCreator are honored"() {
        when:
        def xml = writeXml(Country.ITALY)

        then:
        xml == "<Country>Italy</Country>"
        readXml(xml, Country) == Country.ITALY
    }

    def "root POJO arrays round trip"() {
        given:
        def type = Argument.of(ParityResource[])
        def input = [
            new ParityResource(id: 1, name: "first"),
            new ParityResource(id: 2, name: "second")
        ] as ParityResource[]

        when:
        def xml = writeXml(type, input)
        ParityResource[] decoded = readXml(xml, type)

        then:
        decoded*.id == [1L, 2L]
        decoded*.name == ["first", "second"]
    }

    def "empty wrapped list with whitespace decodes as an empty list"() {
        when:
        def decoded = readXml(
            "<ChannelSet><setId>2</setId><channels>\n  </channels></ChannelSet>",
            ChannelSet
        )

        then:
        decoded.setId == "2"
        decoded.channels != null
        decoded.channels.empty
    }

    def "empty strings and null string list items retain their positions"() {
        given:
        def type = Argument.listOf(String)
        def input = ["", "test", null, "test2"]

        when:
        def decoded = readXml(writeXml(type, input), type)

        then:
        decoded.size() == 4
        decoded[0] == ""
        decoded[1] == "test"
        decoded[2] == null || decoded[2] == ""
        decoded[3] == "test2"
    }

    def "JsonUnwrapped nested properties round trip"() {
        given:
        def input = new Unwrapping(name: "Joe", location: new Location(x: 15, y: 27))

        when:
        def xml = writeXml(input)
        def decoded = readXml(xml, Unwrapping)

        then:
        xml.contains("<loc.x>15</loc.x>")
        xml.contains("<loc.y>27</loc.y>")
        !xml.contains("<location>")
        decoded.name == "Joe"
        decoded.location.x == 15
        decoded.location.y == 27
    }

    def "multiple unwrapped lists preserve attributes and stay distinct"() {
        given:
        def input = new UnwrappedLists(
            firstBar: [new Bar(id: 1, value: "FIRST")],
            secondBar: [new Bar(id: 2, value: "SECOND")]
        )

        when:
        def xml = writeXml(input)
        def decoded = readXml(xml, UnwrappedLists)

        then:
        decoded.firstBar*.id == [1]
        decoded.firstBar*.value == ["FIRST"]
        decoded.secondBar*.id == [2]
        decoded.secondBar*.value == ["SECOND"]
    }

    def "empty beans deserialize from an empty root element"() {
        expect:
        readXml("<EmptyBean/>", EmptyBean) != null
    }

    def "JacksonXmlRootElement customizes the root local name and namespace"() {
        given:
        def input = new CustomRootBean(id: "root-id")

        when:
        def xml = writeXml(input)
        def decoded = readXml(xml, CustomRootBean)

        then:
        xml.startsWith('<custom-root xmlns="urn:custom-root"')
        xml.contains('id="root-id"')
        decoded.id == "root-id"
    }

    def "JacksonXmlRootElement applies a namespace when localName uses its default"() {
        when:
        def xml = writeXml(new DefaultRootNamespaceBean())

        then:
        xml.startsWith('<DefaultRootNamespaceBean xmlns="urn:default-root"')
        readXml(xml, DefaultRootNamespaceBean) != null
    }

    def "JacksonXmlText writes and reads direct element text after attributes"() {
        given:
        def input = new TextBean(language: "en", content: "hello <xml> & goodbye")

        when:
        def xml = writeXml(input)
        def decoded = readXml(xml, TextBean)

        then:
        xml == '<TextBean language="en">hello &lt;xml> &amp; goodbye</TextBean>'
        decoded.language == "en"
        decoded.content == "hello <xml> & goodbye"
    }

    def "JacksonXmlCData writes String properties as CDATA"() {
        given:
        def input = new CDataBean(content: "<xml> & text")

        when:
        def xml = writeXml(input)
        def decoded = readXml(xml, CDataBean)

        then:
        xml == '<CDataBean><content><![CDATA[<xml> & text]]></content></CDataBean>'
        decoded.content == "<xml> & text"
    }

    def "JacksonXmlCData applies to unwrapped String collection items"() {
        given:
        def input = new CDataListBean(values: ["<first>", "second & value"])

        when:
        def xml = writeXml(input)
        def decoded = readXml(xml, CDataListBean)

        then:
        xml == '<CDataListBean><values><![CDATA[<first>]]></values><values><![CDATA[second & value]]></values></CDataListBean>'
        decoded.values == ["<first>", "second & value"]
    }

    def "JacksonXmlText and JacksonXmlCData combine for direct CDATA content"() {
        given:
        def input = new CDataTextBean(content: "<xml> & text")

        when:
        def xml = writeXml(input)
        def decoded = readXml(xml, CDataTextBean)

        then:
        xml == '<CDataTextBean><![CDATA[<xml> & text]]></CDataTextBean>'
        decoded.content == "<xml> & text"
    }

    def "JacksonXmlText reads nested direct text without consuming the parent"() {
        given:
        def input = new NestedTextBean(
            value: new TextBean(language: "en", content: "nested text"),
            trailing: "after"
        )

        when:
        def xml = writeXml(input)
        def decoded = readXml(xml, NestedTextBean)

        then:
        xml.contains('<value language="en">nested text</value>')
        xml.contains('<trailing>after</trailing>')
        decoded.value.language == "en"
        decoded.value.content == "nested text"
        decoded.trailing == "after"
    }

    def "JacksonXmlElementWrapper applies its namespace independently of item namespace"() {
        given:
        def input = new NamespacedWrapperBean(values: ["one", "two"])

        when:
        def xml = writeXml(input)
        def decoded = readXml(xml, NamespacedWrapperBean)

        then:
        xml == '<NamespacedWrapperBean><wstxns1:items xmlns:wstxns1="urn:wrapper"><item>one</item><item>two</item></wstxns1:items></NamespacedWrapperBean>'
        decoded.values == ["one", "two"]
    }

    def "disabled JacksonXmlText and JacksonXmlCData retain normal element encoding"() {
        given:
        def input = new DisabledXmlAnnotationsBean(text: "text", cdata: "<xml>")

        when:
        def xml = writeXml(input)
        def decoded = readXml(xml, DisabledXmlAnnotationsBean)

        then:
        xml.startsWith('<DisabledXmlAnnotationsBean>')
        xml.contains('<cdata>&lt;xml></cdata>')
        xml.contains('<text>text</text>')
        !xml.contains('<![CDATA[')
        decoded.text == "text"
        decoded.cdata == "<xml>"
    }

    def "generated bean serde carries text and CDATA key metadata"() {
        given:
        def input = new JacksonXmlAnnotationBeans.TextBean(language: "en", content: "<generated> & text")

        when:
        def xml = writeXml(input)
        def decoded = readXml(xml, JacksonXmlAnnotationBeans.TextBean)

        then:
        xml == '<TextBean language="en"><![CDATA[<generated> & text]]></TextBean>'
        decoded.language == input.language
        decoded.content == input.content
    }

    def "generated bean serde carries wrapper namespace and collection CDATA metadata"() {
        given:
        def input = new JacksonXmlAnnotationBeans.CollectionBean(values: ["<one>", "two & three"])

        when:
        def xml = writeXml(input)
        def decoded = readXml(xml, JacksonXmlAnnotationBeans.CollectionBean)

        then:
        xml == '<CollectionBean><wstxns1:items xmlns:wstxns1="urn:generated-wrapper"><item><![CDATA[<one>]]></item><item><![CDATA[two & three]]></item></wstxns1:items></CollectionBean>'
        decoded.values == input.values
    }

    enum TestEnum {
        A,
        B,
        C
    }

    @Serdeable
    static class EnumBean {
        TestEnum value
    }

    @Serdeable
    enum Country {
        ITALY("Italy"),
        NETHERLANDS("Netherlands")

        private final String value

        Country(String value) {
            this.value = value
        }

        @JsonValue
        String getValue() {
            return value
        }

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        static Country fromValue(String value) {
            return values().find { it.value == value }
        }
    }

    @Serdeable
    static class ParityResource {
        long id
        String name
    }

    @Serdeable
    static class ChannelSet {
        String setId

        @JacksonXmlElementWrapper(useWrapping = true)
        List<Channel> channels
    }

    @Serdeable
    static class Channel {
        String channelId
    }

    @Serdeable
    @JsonPropertyOrder(["name", "location"])
    static class Unwrapping {
        String name

        @JsonUnwrapped(prefix = "loc.")
        Location location
    }

    @Serdeable
    @JsonPropertyOrder(["x", "y"])
    static class Location {
        int x
        int y
    }

    @Serdeable
    static class UnwrappedLists {
        @JacksonXmlElementWrapper(useWrapping = false)
        List<Bar> firstBar

        @JacksonXmlElementWrapper(useWrapping = false)
        List<Bar> secondBar
    }

    @Serdeable
    static class Bar {
        @JacksonXmlProperty(isAttribute = true)
        int id

        String value
    }

    @Serdeable
    static class EmptyBean {
    }

    @Serdeable
    @JacksonXmlRootElement(localName = "custom-root", namespace = "urn:custom-root")
    static class CustomRootBean {
        @JacksonXmlProperty(isAttribute = true)
        String id
    }

    @Serdeable
    @JacksonXmlRootElement(namespace = "urn:default-root")
    static class DefaultRootNamespaceBean {
    }

    @Serdeable
    static class TextBean {
        @JacksonXmlProperty(isAttribute = true)
        String language

        @JacksonXmlText
        String content
    }

    @Serdeable
    static class CDataBean {
        @JacksonXmlCData
        String content
    }

    @Serdeable
    static class CDataListBean {
        @JacksonXmlCData
        @JacksonXmlElementWrapper(useWrapping = false)
        List<String> values
    }

    @Serdeable
    static class CDataTextBean {
        @JacksonXmlText
        @JacksonXmlCData
        String content
    }

    @Serdeable
    static class NestedTextBean {
        TextBean value
        String trailing
    }

    @Serdeable
    static class NamespacedWrapperBean {
        @JacksonXmlElementWrapper(localName = "items", namespace = "urn:wrapper")
        @JacksonXmlProperty(localName = "item")
        List<String> values
    }

    @Serdeable
    static class DisabledXmlAnnotationsBean {
        @JacksonXmlText(false)
        String text

        @JacksonXmlCData(false)
        String cdata
    }
}
