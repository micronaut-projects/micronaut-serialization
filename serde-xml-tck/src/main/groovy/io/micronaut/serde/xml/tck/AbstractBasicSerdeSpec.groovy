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

import io.micronaut.serde.config.annotation.SerdeConfig
import io.micronaut.test.support.TestPropertyProvider
import spock.lang.Specification

abstract class AbstractBasicSerdeSpec extends Specification implements TestPropertyProvider{

    abstract Object getXmlMapper();

    def "Simple Bean"(){
        given:
            def bean = new SimpleBean(21, "Hamza")
            def expectedXml = "<SimpleBean>" +
                                    "<age>21</age>" +
                                    "<name>Hamza</name>" +
                                "</SimpleBean>";
        when:
            def xml = xmlMapper.writeValueAsString(bean)
        then:
            xml == expectedXml

    }

    def "Outer and Inner Bean"(){
        given:
            def inner = new SimpleBean(21, "Hamza");
            def bean = new ObjectBean(inner);
        when:
            def xml = xmlMapper.writeValueAsString(bean)
        then:
            xml == "<ObjectBean><simpleBeans><age>21</age><name>Hamza</name></simpleBeans></ObjectBean>"

    }

    def "Custom Bean"() {
        given:
            def bean = new CustomBean("A1", "A2", List.of("B1", "B2"));
        when:
            def xml = xmlMapper.writeValueAsString(bean)
        then:
            xml == "<CustomBean><A1>A1</A1><B1>A2</B1><C1><C1>B1</C1><C1>B2</C1></C1></CustomBean>"
    }

    def "Nested List"(){
        given:
            def bean = new SimpleBean(21, "Hamza");
            def nestedList = new NestedList(List.of(bean));
        when:
            def xml = xmlMapper.writeValueAsString(nestedList)
        then:
            xml == "<NestedList><nestedLists><nestedLists><age>21</age><name>Hamza</name></nestedLists></nestedLists></NestedList>"
    }

    @Override
    Map<String, String> getProperties() {
//        ["micronaut.serde.serialization.inclusion": SerdeConfig.SerInclude.ALWAYS.name()]
    }
}
