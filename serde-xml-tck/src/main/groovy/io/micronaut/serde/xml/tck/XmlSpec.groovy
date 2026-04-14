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
import io.micronaut.json.JsonMapper
import tools.jackson.databind.JsonNode

import java.nio.charset.StandardCharsets

trait XmlSpec {


    abstract JsonMapper getXmlMapper()


    String writeXml(Object bean) {
        new String(getXmlMapper().writeValueAsBytes(bean), StandardCharsets.UTF_8)
    }

    String writeXml(Argument argument, Object bean) {
        new String(getXmlMapper().writeValueAsBytes(argument, bean), StandardCharsets.UTF_8)
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

    boolean XmlMatches(String result, String expected) {
        result == expected
    }

    boolean objRepresentationMatches(Object obj, String xml2) {
        def xml1 = xmlMapper.writeValueAsBytes(obj)
        def xml1_string = new String(xml1, StandardCharsets.UTF_8)
        assert xml1_string == xml2
        xml1_string == xml2
    }

    def <T> T serializeDeserialize(T obj) {
        return serializeDeserializeAs(obj, Argument.of(obj.getClass()))
    }

    def <T> T serializeDeserializeAs(T obj, Argument type) {
        def output = getXmlMapper().writeValueAsBytes(obj)
        return getXmlMapper().readValue(output, type) as T
    }
}
