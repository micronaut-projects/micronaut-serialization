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
