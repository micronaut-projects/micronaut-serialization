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

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonRootName
import io.micronaut.serde.annotation.SerdeableGenerated
import spock.lang.Specification
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty

/**
 * Shared TCK spec for XML namespace handling.
 *
 * <p>Reads and writes go through the {@link XmlSpec} trait so each runner
 * supplies its own mapper without this class knowing anything about the
 * underlying serializer/deserializer implementation.</p>
 */
abstract class AbstractXmlNamespaceSpec extends Specification implements XmlSpec {

    def "namespaced child element - localName + namespace on a property"() {
        given:
        def bean = new NamespacedChildBean()

        when:
        String xml = writeXml(bean)
        def root = parseXmlRoot(xml)
        def child = root.getElementsByTagNameNS("uri:child", "ChildXML").item(0)

        then:
        root.localName == "NamespacedChildBean"
        child != null
        child.textContent == "v"
    }

    def "namespaced attribute - namespace + isAttribute on a property"() {
        given:
        def bean = new NamespacedAttrBean()

        when:
        String xml = writeXml(bean)
        def root = parseXmlRoot(xml)

        then: "the attribute is emitted with a namespaced prefix on the owning element"
        root.getAttributeNS("http://foo", "other") == "3"
    }

    def "namespaced root - JsonRootName(value, namespace) round trips"() {
        given:
        def bean = new NamespacedRootBean()

        when:
        String xml = writeXml(bean)
        def decoded = readXml(xml, NamespacedRootBean)

        then:
        xml == '<nsRoot xmlns="http://foo"></nsRoot>'
        decoded != null
    }

    @SerdeableGenerated(skip = true)
    @JsonRootName(value = "nsRoot", namespace = "http://foo")
    static class NamespacedRootBean {
    }

    def "namespace from JsonProperty merges with JacksonXmlProperty isAttribute"() {
        given:
        def bean = new MergedNsAttrBean()

        when:
        String xml = writeXml(bean)
        def decoded = readXml(xml, MergedNsAttrBean)
        def root = parseXmlRoot(xml)

        then: "the namespace from JsonProperty is honoured on the attribute"
        root.getAttributeNS("uri:ns1", "value") == "3"
        decoded.attr == "3"
    }

    def "JsonProperty supplies the local name and namespace for an XML element"() {
        given:
        def bean = new JsonNamespacedChildBean(child: 4)

        when:
        String xml = writeXml(bean)
        def decoded = readXml(xml, JsonNamespacedChildBean)

        then:
        xml.startsWith('<Root><')
        xml.contains('ChildJSON')
        xml.contains('uri:child')
        decoded.child == 4
    }

    def "JsonRootName and JsonProperty namespaces are honored together"() {
        given:
        def bean = new JsonNamespacedPerson(name: "hello")

        when:
        String xml = writeXml(bean)
        def decoded = readXml(xml, JsonNamespacedPerson)

        then:
        xml.startsWith('<person xmlns="http://example.org/person"')
        xml.contains('name')
        xml.contains('http://example.org/personJSON')
        xml.contains('hello')
        decoded.name == "hello"
    }

    def "namespaced child element deserializes by local name"() {
        when:
        def bean = readXml(
                '<NamespacedChildBean><ns:ChildXML xmlns:ns="uri:other">v</ns:ChildXML></NamespacedChildBean>',
                NamespacedChildBean
        )

        then:
        bean.child == "v"
    }

    def "namespaced nested object keeps its normal serializer"() {
        given:
        def bean = new NamespacedObjectBean(child: new NestedBean(value: "v"))

        when:
        def xml = writeXml(bean)
        def decoded = readXml(xml, NamespacedObjectBean)

        then:
        xml.contains("<value>v</value>")
        !xml.contains(NestedBean.name + "@")
        decoded.child.value == "v"
    }

    @SerdeableGenerated(skip = true)
    static class MergedNsAttrBean {
        @JsonProperty(value = "value", namespace = "uri:ns1")
        @JacksonXmlProperty(isAttribute = true)
        String attr = "3"

        String getAttr() { return attr }

        void setAttr(String attr) { this.attr = attr }
    }

    @SerdeableGenerated(skip = true)
    @JsonRootName("Root")
    static class JsonNamespacedChildBean {
        @JsonProperty(value = "ChildJSON", namespace = "uri:child")
        int child
    }

    @SerdeableGenerated(skip = true)
    @JsonRootName(value = "person", namespace = "http://example.org/person")
    static class JsonNamespacedPerson {
        @JsonProperty(namespace = "http://example.org/personJSON")
        String name
    }

    @SerdeableGenerated(skip = true)
    static class NamespacedAttrBean {
        @JacksonXmlProperty(namespace = "http://foo", isAttribute = true, localName = "other")
        String attr = "3"

        String getAttr() {
            return attr
        }

        void setAttr(String attr) {
            this.attr = attr
        }
    }

    def "reserved xml: namespace prefix is not bound and surfaces literally on serialization"() {
        given:
        def bean = new Issue395Bean()

        when:
        String xml = writeXml(bean)

        then: "the reserved http://www.w3.org/XML/1998/namespace URI surfaces literally as the 'xml:' prefix with no xmlns:xml declaration"
        xml.trim() == '<Issue395Bean xml:lang="en-US"></Issue395Bean>'
    }

    @SerdeableGenerated(skip = true)
    static class Issue395Bean {
        @JacksonXmlProperty(isAttribute = true,
                namespace = "http://www.w3.org/XML/1998/namespace",
                localName = "lang")
        String lang = "en-US"

        String getLang() { return lang }
        void setLang(String lang) { this.lang = lang }
    }

    @SerdeableGenerated(skip = true)
    static class NamespacedChildBean {
        @JacksonXmlProperty(namespace = "uri:child", localName = "ChildXML")
        String child = "v"

        String getChild() {
            return child
        }

        void setChild(String child) {
            this.child = child
        }
    }

    @SerdeableGenerated(skip = true)
    static class NamespacedObjectBean {
        @JacksonXmlProperty(namespace = "uri:child", localName = "child")
        NestedBean child
    }

    @SerdeableGenerated(skip = true)
    static class NestedBean {
        String value
    }

}
