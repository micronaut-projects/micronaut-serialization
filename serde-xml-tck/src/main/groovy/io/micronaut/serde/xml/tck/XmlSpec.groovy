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

import io.micronaut.core.type.Argument
import tools.jackson.databind.JsonNode

import java.io.InputStream
import java.nio.charset.StandardCharsets
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

trait XmlSpec {

    abstract <T> T readXml(String xml, Argument<T> type)

    abstract <T> T readXml(byte[] xml, Argument<T> type)

    abstract <T> T readXml(InputStream xml, Argument<T> type)

    /**
     * Read XML using a mapper configured with the given Micronaut-style properties.
     *
     * <p>Keys are canonical Micronaut configuration keys (e.g.
     * {@code micronaut.serde.format.xml.xml-read-features.EMPTY_ELEMENT_AS_NULL}). Each
     * concrete runner adapts these to its native configuration model.</p>
     */
    abstract <T> T readXmlWithProperties(Map<String, Object> properties, String xml, Class<T> type)

    abstract String writeXml(Object bean)

    abstract String writeXml(Argument<?> argument, Object bean)

    abstract byte[] writeXmlAsBytes(Object bean)

    abstract byte[] writeXmlAsBytes(Argument<?> argument, Object bean)

    def <T> T readXml(String xml, Class<T> type) {
        readXml(xml, Argument.of(type))
    }

    def <T> T readXml(byte[] xml, Class<T> type) {
        readXml(xml, Argument.of(type))
    }

    def <T> T readXml(InputStream xml, Class<T> type) {
        readXml(xml, Argument.of(type))
    }

    /**
     * Converting JSON string to XML with Jackson's JsonNode.
     */
    String xmlString(String json) {
        JsonNode tree = JACKSON_JSON.readTree(json)
        return JACKSON_XML.writeValueAsString(tree)
    }

    byte[] xmlBytes(String xml) {
        return xml.getBytes(StandardCharsets.UTF_8)
    }

    org.w3c.dom.Element parseXmlRoot(String xml) {
        def factory = DocumentBuilderFactory.newInstance()
        factory.namespaceAware = true
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        factory.newDocumentBuilder()
            .parse(new ByteArrayInputStream(xml.bytes))
            .documentElement
    }

    boolean xmlMatches(String result, String expected) {
        result == expected
    }

    boolean XmlMatches(String result, String expected) {
        xmlMatches(result, expected)
    }

    boolean objRepresentationMatches(Object obj, String xml) {
        xmlMatches(writeXml(obj), xml)
    }

    boolean objRepresentationMatches(Argument<?> argument, Object obj, String xml) {
        xmlMatches(writeXml(argument, obj), xml)
    }

    def <T> T serializeDeserialize(T obj) {
        return serializeDeserializeAs(obj, Argument.of(obj.getClass()))
    }

    def <T> T serializeDeserializeAs(T obj, Argument<T> type) {
        return readXml(writeXmlAsBytes(type, obj), type)
    }
}
