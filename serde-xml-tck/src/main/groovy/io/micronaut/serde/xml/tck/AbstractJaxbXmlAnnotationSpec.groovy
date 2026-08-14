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

import jakarta.xml.bind.annotation.XmlAttribute
import jakarta.xml.bind.annotation.XmlAccessOrder
import jakarta.xml.bind.annotation.XmlAccessorOrder
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlElementWrapper
import jakarta.xml.bind.annotation.XmlEnum
import jakarta.xml.bind.annotation.XmlEnumValue
import jakarta.xml.bind.annotation.XmlRootElement
import jakarta.xml.bind.annotation.XmlTransient
import jakarta.xml.bind.annotation.XmlType
import jakarta.xml.bind.annotation.XmlValue
import spock.lang.Specification

/** Shared JAXB annotation scenarios for XML backends. */
abstract class AbstractJaxbXmlAnnotationSpec extends Specification implements XmlSpec {

    def "JAXB root, element, attribute, text, and inline collections round trip"() {
        given:
        def value = new JaxbBook(isbn: "978", title: "Serde", authors: ["Ana", "Ben"], note: "published")

        when:
        def xml = writeXml(value)
        def decoded = readXml(xml, JaxbBook)

        then:
        xml == '<book xmlns="urn:books" isbn="978"><title>Serde</title><author>Ana</author><author>Ben</author>published</book>'
        decoded.isbn == "978"
        decoded.title == "Serde"
        decoded.authors == ["Ana", "Ben"]
    }

    def "JAXB wrappers and transient properties are honored"() {
        given:
        def value = new JaxbLibrary(books: ["Serde"], ignored: "secret")

        when:
        def xml = writeXml(value)
        def decoded = readXml(xml, JaxbLibrary)

        then:
        xml == '<jaxbLibrary><books><book>Serde</book></books></jaxbLibrary>'
        decoded.books == ["Serde"]
        decoded.ignored == null
    }

    def "JAXB defaults derive root and property names and inline collections"() {
        given:
        def value = new JaxbDefaults(title: "Serde", authors: ["Ana", "Ben"])

        when:
        def xml = writeXml(value)
        def decoded = readXml(xml, JaxbDefaults)

        then:
        xml == '<jaxbDefaults><title>Serde</title><authors>Ana</authors><authors>Ben</authors></jaxbDefaults>'
        decoded.title == "Serde"
        decoded.authors == ["Ana", "Ben"]
    }

    def "JAXB type and alphabetical orders are applied"() {
        expect:
        writeXml(new JaxbTypeOrder(first: "one", second: "two", third: "three")) ==
            '<jaxbTypeOrder><third>three</third><first>one</first><second>two</second></jaxbTypeOrder>'
        writeXml(new JaxbAlphabeticalOrder(zebra: "z", apple: "a", middle: "m")) ==
            '<jaxbAlphabeticalOrder><apple>a</apple><middle>m</middle><zebra>z</zebra></jaxbAlphabeticalOrder>'
    }

    def "JAXB namespaces apply to root elements, attributes, elements, and wrappers"() {
        given:
        def value = new JaxbNamespacedBook(code: "A1", chapter: "intro", chapters: ["one"])

        when:
        def xml = writeXml(value)
        def decoded = readXml(xml, JaxbNamespacedBook)

        then:
        xml.contains('xmlns="urn:root"')
        xml.contains('urn:attribute')
        xml.contains('urn:chapter')
        xml.contains('urn:chapters')
        xml.contains('>intro<')
        xml.contains('>one<')
        decoded.code == "A1"
        decoded.chapter == "intro"
        decoded.chapters == ["one"]
    }

    def "JAXB XmlValue writes direct element text with attributes"() {
        expect:
        writeXml(new JaxbTextValue(language: "en", value: "hello")) == '<jaxbTextValue language="en">hello</jaxbTextValue>'
    }

    def "JAXB enum lexical values are used"() {
        expect:
        writeXml(JaxbEdition.SECOND) == '<JaxbEdition>second-edition</JaxbEdition>'
        readXml('<JaxbEdition>second-edition</JaxbEdition>', JaxbEdition) == JaxbEdition.SECOND
    }

    @XmlRootElement(name = "book", namespace = "urn:books")
    @XmlType(propOrder = ["title", "authors"])
    static class JaxbBook {
        @XmlAttribute(name = "isbn")
        String isbn

        @XmlElement(name = "title")
        String title

        @XmlElement(name = "author")
        List<String> authors

        @XmlValue
        String note
    }

    @XmlRootElement
    static class JaxbLibrary {
        @XmlElementWrapper(name = "books")
        @XmlElement(name = "book")
        List<String> books

        @XmlTransient
        String ignored
    }

    @XmlRootElement
    static class JaxbDefaults {
        String title
        List<String> authors
    }

    @XmlRootElement
    @XmlType(propOrder = ["third", "first", "second"])
    static class JaxbTypeOrder {
        String first
        String second
        String third
    }

    @XmlRootElement
    @XmlAccessorOrder(XmlAccessOrder.ALPHABETICAL)
    static class JaxbAlphabeticalOrder {
        String zebra
        String apple
        String middle
    }

    @XmlRootElement(name = "book", namespace = "urn:root")
    static class JaxbNamespacedBook {
        @XmlAttribute(name = "code", namespace = "urn:attribute")
        String code

        @XmlElement(name = "chapter", namespace = "urn:chapter")
        String chapter

        @XmlElementWrapper(name = "chapters", namespace = "urn:chapters")
        @XmlElement(name = "chapter")
        List<String> chapters
    }

    @XmlRootElement
    static class JaxbTextValue {
        @XmlAttribute
        String language

        @XmlValue
        String value
    }

    @XmlEnum
    static enum JaxbEdition {
        FIRST,
        @XmlEnumValue("second-edition")
        SECOND
    }

}
