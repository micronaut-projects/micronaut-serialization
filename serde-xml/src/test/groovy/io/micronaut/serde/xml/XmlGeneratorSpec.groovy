package io.micronaut.serde.xml

import io.micronaut.core.type.Argument
import io.micronaut.serde.Encoder
import spock.lang.Specification

import javax.xml.stream.XMLOutputFactory
import java.nio.charset.StandardCharsets

class XmlGeneratorSpec extends Specification {

    private static String writeXml(@DelegatesTo(Encoder) Closure<?> closure) {
        def output = new ByteArrayOutputStream()
        def writer = XMLOutputFactory.newFactory()
            .createXMLStreamWriter(output, StandardCharsets.UTF_8.name())
        def encoder = new XmlGenerator(writer)

        closure.call(encoder)

        writer.close()
        output.toString(StandardCharsets.UTF_8.name())
    }

    def "encode object with scalar properties"() {
        expect:
        writeXml { Encoder encoder ->
            def object = encoder.encodeObject(Argument.of(Root))
            object.encodeKey('name')
            object.encodeString('Bob')
            object.encodeKey('age')
            object.encodeInt(42)
            object.finishStructure()
        } == '<Root><name>Bob</name><age>42</age></Root>'
    }

    def "encode wrapped array"() {
        expect:
        writeXml { Encoder encoder ->
            def object = encoder.encodeObject(Argument.of(Root))
            object.encodeKey('items')

            def array = object.encodeArray(Argument.of(List))
            array.encodeString('a')
            array.encodeString('b')
            array.finishStructure()

            object.finishStructure()
        } == '<Root><items><items>a</items><items>b</items></items></Root>'
    }

    def "encode inline array"() {
        expect:
        writeXml { Encoder encoder ->
            def object = encoder.encodeObject(Argument.of(Root))
            object.encodeKey('item')

            def array = ((XmlGenerator) object).encodeInlineArray(Argument.of(List))
            array.encodeString('a')
            array.encodeString('b')
            array.finishStructure()

            object.finishStructure()
        } == '<Root><item>a</item><item>b</item></Root>'
    }

    def "encode attribute and child element"() {
        expect:
        writeXml { Encoder encoder ->
            def object = encoder.encodeObject(Argument.of(Root))
            object.encodeKey('id')
            ((XmlGenerator) object).writeAttributeForCurrentKey('123')

            object.encodeKey('name')
            object.encodeString('Bob')

            object.finishStructure()
        } == '<Root id="123"><name>Bob</name></Root>'
    }

    def "encode null as empty element"() {
        expect:
        writeXml { Encoder encoder ->
            def object = encoder.encodeObject(Argument.of(Root))
            object.encodeKey('empty')
            object.encodeNull()
            object.finishStructure()
        } == '<Root><empty/></Root>'
    }

    static class Root {
    }
}
