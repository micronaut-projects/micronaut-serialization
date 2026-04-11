package io.micronaut.serde.xml.tck

import io.micronaut.json.JsonMapper
import io.micronaut.serde.config.annotation.SerdeConfig
import io.micronaut.test.support.TestPropertyProvider
import spock.lang.Specification

abstract class AbstractBasicSerdeSpec extends Specification implements TestPropertyProvider{

    abstract JsonMapper getXmlMapper();

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
            xml == "<ObjectBean>" +
                        "<simpleBeans>" +
                            "<SimpleBean>" +
                                "<age>21</age>" +
                                "<name>Hamza</name>" +
                            "</SimpleBean>" +
                        "</simpleBeans>" +
                    "</ObjectBean>"

    }

    def "Custom Bean"() {
        given:
            def bean = new CustomBean("A1", List.of("B1", "B2"), "A2");
        when:
            def xml = xmlMapper.writeValueAsString(bean)
        then:
            xml == "<CustomBean>" +
                        "<a1>A1</a1>" +
                        "<c1>" +
                            "<c1>B1</c1>" +
                            "<c1>B2</c1>" +
                        "</c1>" +
                        "<b1>A2</b1>" +
                    "</CustomBean>"
    }

    def "Nested List"(){
        given:
            def bean = new SimpleBean(21, "Hamza");
            def nestedList = new NestedList(List.of(bean));
        when:
            def xml = xmlMapper.writeValueAsString(nestedList)
        then:
            xml == "<NestedList>" +
                        "<nestedLists>" +
                            "<SimpleBean>" +
                                "<age>21</age>" +
                                "<name>Hamza</name>" +
                            "</SimpleBean>" +
                        "</nestedLists>" +
                    "</NestedList>"
    }

    @Override
    Map<String, String> getProperties() {
        ["micronaut.serde.serialization.inclusion": SerdeConfig.SerInclude.ALWAYS.name()]
    }
}
