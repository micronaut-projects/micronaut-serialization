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

import java.nio.charset.StandardCharsets
import java.util.stream.Stream

abstract class JavaCollectionsTestSpec extends Specification {

    abstract Object getXmlMapper()

    @Serdeable
    static class ByteArrayWrapper {
        byte[] values
    }

    @Serdeable
    static class IntArrayWrapper {
        int[] values
    }

    @Serdeable
    static class IntListWrapper {
        List<Integer> values
    }

    @Serdeable
    static class DoubleListWrapper {
        List<Double> values
    }

    def "Test Stream Of"(){
        given:
        List<String> input = Stream.of('a', 'b', 'c');

        when:
        def xml = xmlMapper.writeValueAsString(input)

        then:
        xml == "<ArrayList><item>a</item><item>b</item><item>c</item></ArrayList>"

    }

    def "Test byte array base64 encoding"() {
        given:
        def bean = new ByteArrayWrapper(values: "sure.".getBytes(StandardCharsets.US_ASCII))

        when:
        def xml = xmlMapper.writeValueAsString(bean)

        then:
        xml == '<ByteArrayWrapper><values>c3VyZS4=</values></ByteArrayWrapper>'
    }

    def "Test int array encoding"() {
        given:
        def input = new IntArrayWrapper(values: [1, -1, 0, 98, 127] as int[])

        when:
        def xml = xmlMapper.writeValueAsString(input)

        then:
        xml == '<IntArrayWrapper><values><values>1</values><values>-1</values><values>0</values><values>98</values><values>127</values></values></IntArrayWrapper>'
    }

    def "Test decimal list encoding"() {
        given:
        def input = new DoubleListWrapper(values: [0.0d, 0.25d, -0.125d, 10.5d, 9875.0d])

        when:
        def xml = xmlMapper.writeValueAsString(input)

        then:
        xml.contains('<values>0.0</values>')
        xml.contains('<values>0.25</values>')
        xml.contains('<values>-0.125</values>')
        xml.contains('<values>10.5</values>')
        xml.contains('<values>9875.0</values>')
    }

    def "Test integer list encoding"() {
        given:
        def intListWrapper = new IntListWrapper(values: [4, 5, 6])

        when:
        def listXml = xmlMapper.writeValueAsString(intListWrapper)

        then:
        listXml == '<IntListWrapper><values><values>4</values><values>5</values><values>6</values></values></IntListWrapper>'
    }



}
