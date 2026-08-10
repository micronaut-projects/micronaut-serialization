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
import io.micronaut.serde.annotation.Serdeable
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

        then:
        xml == '<NamespacedChildBean><wstxns1:ChildXML xmlns:wstxns1="uri:child">v</wstxns1:ChildXML></NamespacedChildBean>'
    }

    def "namespaced attribute - namespace + isAttribute on a property"() {
        given:
        def bean = new NamespacedAttrBean()

        when:
        String xml = writeXml(bean)

        then: "the attribute is emitted with a namespaced prefix on the owning element"
        xml == '<NamespacedAttrBean xmlns:wstxns1="http://foo" wstxns1:other="3"></NamespacedAttrBean>'
    }

    def "namespaced root - JsonRootElement(localName, namespace)"() {
        given:
        def bean = new NamespacedRootBean()

        when:
        String xml = writeXml(bean)

        then:
        xml == '<nsRoot xmlns="http://foo"></nsRoot>'
    }

    @Serdeable
    @JsonRootName(value = "nsRoot", namespace = "http://foo")
    static class NamespacedRootBean {
    }

    def "namespace from JsonProperty merges with JacksonXmlProperty isAttribute"() {
        given:
        def bean = new MergedNsAttrBean()

        when:
        String xml = writeXml(bean)

        then: "the namespace from JsonProperty is honoured on the attribute"
        xml == '<MergedNsAttrBean xmlns:wstxns1="uri:ns1" wstxns1:value="3"></MergedNsAttrBean>'
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

    @Serdeable
    static class MergedNsAttrBean {
        @JsonProperty(value = "value", namespace = "uri:ns1")
        @JacksonXmlProperty(isAttribute = true)
        String attr = "3"

        String getAttr() { return attr }

        void setAttr(String attr) { this.attr = attr }
    }

    @Serdeable
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

    @Serdeable
    static class Issue395Bean {
        @JacksonXmlProperty(isAttribute = true,
                namespace = "http://www.w3.org/XML/1998/namespace",
                localName = "lang")
        String lang = "en-US"

        String getLang() { return lang }
        void setLang(String lang) { this.lang = lang }
    }

    @Serdeable
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

    @Serdeable
    static class NamespacedObjectBean {
        @JacksonXmlProperty(namespace = "uri:child", localName = "child")
        NestedBean child
    }

    @Serdeable
    static class NestedBean {
        String value
    }
}
