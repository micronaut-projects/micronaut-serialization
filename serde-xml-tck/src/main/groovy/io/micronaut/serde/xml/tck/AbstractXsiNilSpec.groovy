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

import io.micronaut.serde.annotation.Serdeable
import spock.lang.Specification

abstract class AbstractXsiNilSpec extends Specification implements XmlSpec {

    private static final String XSI_NS_DECL = "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\""

    @Serdeable
    static class DoubleWrapper {
        Double d
    }

    @Serdeable
    static class DoubleWrapper2 {
        Double a
        Double b
    }

    @Serdeable
    static class StringPair {
        String first
        String second
    }

    @Serdeable
    static class Parent366 {
        Level1 level1
    }

    @Serdeable
    static class Level1 {
        Level2 level2
    }

    @Serdeable
    static class Level2 {
        Integer ignored
        String field
    }

    def "xsi:nil='true' empty self-closing element decodes to null"() {
        given:
        def xml = "<DoubleWrapper " + XSI_NS_DECL + "><d xsi:nil=\"true\"/></DoubleWrapper>"

        when:
        def bean = readXml(xml, DoubleWrapper)

        then:
        bean != null
        bean.d == null
    }

    def "xsi:nil='true' element with whitespace text decodes to null"() {
        given:
        def xml = "<DoubleWrapper " + XSI_NS_DECL + "><d xsi:nil=\"true\">  </d></DoubleWrapper>"

        when:
        def bean = readXml(xml, DoubleWrapper)

        then:
        bean != null
        bean.d == null
    }

    def "xsi:nil='false' element with text decodes to value"() {
        given:
        def xml = "<DoubleWrapper " + XSI_NS_DECL + "><d xsi:nil=\"false\">0.25</d></DoubleWrapper>"

        when:
        def bean = readXml(xml, DoubleWrapper)

        then:
        bean != null
        bean.d == 0.25d
    }

    def "xsi:nil mixed siblings - first nil, second value"() {
        given:
        def xml = "<DoubleWrapper2 " + XSI_NS_DECL + ">" +
                "<a xsi:nil=\"true\"></a>" +
                "<b xsi:nil=\"false\">0.25</b>" +
                "</DoubleWrapper2>"

        when:
        def bean = readXml(xml, DoubleWrapper2)

        then:
        bean != null
        bean.a == null
        bean.b == 0.25d
    }

    def "xsi:nil on String field after a non-null sibling decodes to null"() {
        given:
        def xml = "<StringPair " + XSI_NS_DECL + ">" +
                "<first>not null</first><second xsi:nil=\"true\"/>" +
                "</StringPair>"

        when:
        def bean = readXml(xml, StringPair)

        then:
        bean != null
        bean.first == "not null"
        bean.second == null
    }

    def "xsi:nil on String field before a non-null sibling decodes to null"() {
        given:
        def xml = "<StringPair " + XSI_NS_DECL + ">" +
                "<first xsi:nil=\"true\"/><second>not null</second>" +
                "</StringPair>"

        when:
        def bean = readXml(xml, StringPair)

        then:
        bean != null
        bean.first == null
        bean.second == "not null"
    }

    def "xsi:nil on a nested-grandchild leaf does not affect later siblings"() {
        given:
        def xml = "<Parent366 " + XSI_NS_DECL + ">" +
                " <level1>" +
                "  <level2>" +
                "    <ignored xsi:nil=\"true\"/>" +
                "    <field>test-value</field>" +
                "  </level2>" +
                " </level1>" +
                "</Parent366>"

        when:
        def bean = readXml(xml, Parent366)

        then:
        bean != null
        bean.level1 != null
        bean.level1.level2 != null
        bean.level1.level2.ignored == null
        bean.level1.level2.field == "test-value"
    }

    def "xsi:nil mixed siblings - first value, second nil"() {
        given:
        def xml = "<DoubleWrapper2 " + XSI_NS_DECL + ">" +
                "<a xsi:nil=\"false\">0.25</a>" +
                "<b xsi:nil=\"true\"></b>" +
                "</DoubleWrapper2>"

        when:
        def bean = readXml(xml, DoubleWrapper2)

        then:
        bean != null
        bean.a == 0.25d
        bean.b == null
    }
}
