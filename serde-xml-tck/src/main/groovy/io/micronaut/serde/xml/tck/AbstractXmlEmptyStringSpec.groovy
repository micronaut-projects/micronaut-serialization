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
import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.core.annotation.Introspected
import io.micronaut.serde.annotation.Serdeable
import spock.lang.Specification
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty

/**
 * Shared TCK spec for empty XML element handling.
 *
 * Mirrors Jackson's {@code testEmptyElement}:
 *  - default: empty elements decode as {@code ""} (Jackson 3.x default).
 *  - with the implementation-specific opt-in (e.g. {@code EMPTY_ELEMENT_AS_NULL}):
 *    empty elements decode as {@code null}.
 *
 */
abstract class AbstractXmlEmptyStringSpec extends Specification implements XmlSpec {

    void "test empty string"() {
        when: "XML with one populated and one empty element is read"
        def name = readXml("<Name><first>Ryan</first><last></last></Name>", Name.class)

        then: "the populated element keeps its text and the empty element decodes as an empty string"
        name != null
        name.first == "Ryan"
        name.last == ""
    }

    void "empty elements decode as empty string by default"() {
        when: "XML with two empty elements (self-closing and explicit) is read using the default mapper"
        def name = readXml("<Name><first/><last></last></Name>", Name.class)

        then: "both fields decode as empty strings (Jackson 3.x default)"
        name != null
        name.first == ""
        name.last == ""
    }

    void "empty elements decode as null when EMPTY_ELEMENT_AS_NULL is enabled"() {
        when: "the same XML is read through a mapper configured with EMPTY_ELEMENT_AS_NULL enabled"
        def name = readXmlWithProperties(
                ['micronaut.serde.xml.xml-read-features.EMPTY_ELEMENT_AS_NULL': true],
                "<Name><first/><last></last></Name>",
                Name.class
        )

        then: "both fields decode as null instead of empty strings"
        name != null
        name.first == null
        name.last == null
    }

    void "test empty String Element from elemnt and Attr"() {
        when: "XML carrying an empty attribute and an empty element is read"
        def emptyString = readXml("<EmptyString a=''><b /></EmptyString>",
                EmptyStrings25.class)

        then: "both the attribute and the element decode as empty strings"
        emptyString != null
        emptyString.a == ""
        emptyString.b == ""
    }

    void "test empty issue 427"() {
        when: "XML containing an empty nested element bound to a delegating single-arg constructor is read"
        def product = readXml("<product><stuff></stuff></product>", Product427.class)

        then: "the nested bean is constructed with an empty string passed to its delegating ctor"
        product != null
        product.stuff != null
        product.stuff.str == ""
    }

    @Serdeable
    static class Name {
        String first
        String last

        Name() { }

        Name(String f, String l) {
            first = f
            last = l
        }
    }

    @Serdeable
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    static class EmptyStrings25 {
        @JacksonXmlProperty(isAttribute = true)
        public String a = "NOT SET"
        public String b = "NOT SET"
    }

    @Serdeable
    static class Product427 {
        Stuff427 stuff

        Product427(@JsonProperty("stuff") Stuff427 s) { stuff = s }
    }

    @Serdeable
    static class Stuff427 {
        String str

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        Stuff427(String s) { str = s }
    }
}
