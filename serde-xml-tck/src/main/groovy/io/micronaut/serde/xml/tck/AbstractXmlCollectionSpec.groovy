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
import io.micronaut.serde.annotation.SerdeableGenerated
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.stream.Stream

abstract class AbstractXmlCollectionSpec extends Specification implements XmlSpec {

    @SerdeableGenerated(skip = true)
    static class ByteArrayWrapper {
        byte[] values
    }

    @SerdeableGenerated(skip = true)
    static class IntArrayWrapper {
        int[] values
    }

    @SerdeableGenerated(skip = true)
    static class IntListWrapper {
        List<Integer> values
    }

    @SerdeableGenerated(skip = true)
    static class DoubleListWrapper {
        List<Double> values
    }

    @SerdeableGenerated(skip = true)
    static class StringMapWrapper {
        Map<String, String> values
    }

    def "Test Stream Of"(){
        given:
        List<String> input = new ArrayList<>(Stream.of('a', 'b', 'c').toList())

        when:
        def xml = writeXml(input)
        def read = readXml(xml, Argument.listOf(String))

        then:
        xml == "<ArrayList><item>a</item><item>b</item><item>c</item></ArrayList>"
        read == ["a", "b", "c"]

    }

    def "Test byte array base64 encoding"() {
        given:
        def bean = new ByteArrayWrapper(values: "sure.".getBytes(StandardCharsets.US_ASCII))

        when:
        def xml = writeXml(bean)
        def read = readXml(xml, ByteArrayWrapper)

        then:
        xml == '<ByteArrayWrapper><values>c3VyZS4=</values></ByteArrayWrapper>'
        read.values == bean.values
    }

    def "Test int array encoding"() {
        given:
        def input = new IntArrayWrapper(values: [1, -1, 0, 98, 127] as int[])

        when:
        def xml = writeXml(input)
        def read = readXml(xml, IntArrayWrapper)

        then:
        xml == '<IntArrayWrapper><values><values>1</values><values>-1</values><values>0</values><values>98</values><values>127</values></values></IntArrayWrapper>'
        read.values == input.values
    }

    def "Test decimal list encoding"() {
        given:
        def input = new DoubleListWrapper(values: [0.0d, 0.25d, -0.125d, 10.5d, 9875.0d])

        when:
        def xml = writeXml(input)
        def read = readXml(xml, DoubleListWrapper)

        then:
        xml.contains('<values>0.0</values>')
        xml.contains('<values>0.25</values>')
        xml.contains('<values>-0.125</values>')
        xml.contains('<values>10.5</values>')
        xml.contains('<values>9875.0</values>')
        read.values == input.values
    }

    @SerdeableGenerated(skip = true)
    static class SampleResource {
        Long id
        String name
        String description

        SampleResource() { }
        SampleResource(Long id, String name, String description) {
            this.id = id
            this.name = name
            this.description = description
        }
    }

    def "Test root List<POJO> round trip"() {
        given:
        def r1 = new SampleResource(123L, "Albert", "desc")
        def r2 = new SampleResource(123L, "William", "desc2")
        def input = [r1, r2]

        when:
        String xml = writeXml(Argument.listOf(SampleResource), input)
        List<SampleResource> read = readXml(xml, Argument.listOf(SampleResource))

        then:
        // child item element name (independent of root name choice across implementations)
        xml.contains("<item>")
        read.size() == 2
        read.every { it.getClass() == SampleResource }
        read[0].name == "Albert"
        read[0].id == 123L
        read[1].name == "William"
        read[1].description == "desc2"
    }
    /** Covering Already Map with DynaBean in {@link AbstractXmlSerializationAttrSpec} */
    def "Test string map encoding"() {
        given:
        def input = new StringMapWrapper(values: [first: "alpha", second: "beta"])

        when:
        def xml = writeXml(input)
        def read = readXml(xml, StringMapWrapper)

        then:
        xml == '<StringMapWrapper><values><first>alpha</first><second>beta</second></values></StringMapWrapper>'
        read.values == input.values
    }

    def "Test integer list encoding"() {
        given:
        def intListWrapper = new IntListWrapper(values: [4, 5, 6])

        when:
        def listXml = writeXml(intListWrapper)
        def read = readXml(listXml, IntListWrapper)

        then:
        listXml == '<IntListWrapper><values><values>4</values><values>5</values><values>6</values></values></IntListWrapper>'
        read.values == intListWrapper.values
    }
}
