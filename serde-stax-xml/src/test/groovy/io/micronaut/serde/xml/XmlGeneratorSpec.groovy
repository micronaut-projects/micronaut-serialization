package io.micronaut.serde.xml

import io.micronaut.core.type.Argument
import io.micronaut.serde.Encoder
import io.micronaut.serde.KeyDescriptor
import io.micronaut.serde.Keys
import io.micronaut.serde.KeysAwareEncoder
import io.micronaut.serde.config.annotation.SerdeConfig
import spock.lang.Specification

import javax.xml.stream.XMLOutputFactory
import java.nio.charset.StandardCharsets

class XmlGeneratorSpec extends Specification {

    private static String writeXml(@DelegatesTo(Encoder) Closure<?> closure) {
        def output = new ByteArrayOutputStream()
        def writer = XMLOutputFactory.newFactory()
            .createXMLStreamWriter(output, StandardCharsets.UTF_8.name())
        def encoder = new XmlStaxEncoder(writer, (String) null)

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

    def "encode XML property layout from reusable keys"() {
        given:
        def keys = Keys.createWithMetadata(
            KeyDescriptor.create('id', SerdeConfig.XML_ATTRIBUTE_PROPERTY, 'true'),
            KeyDescriptor.create('item', SerdeConfig.META_ANNOTATION_PROPERTY, 'false'),
            KeyDescriptor.create(
                'values',
                SerdeConfig.META_ANNOTATION_PROPERTY, 'true',
                SerdeConfig.WRAPPER_PROPERTY, 'entries'
            )
        )

        expect:
        writeXml { Encoder encoder ->
            def object = encoder.encodeObject(Argument.of(Root))
            def keyEncoder = (KeysAwareEncoder) object

            keyEncoder.encodeKey(keys, 0)
            object.encodeInt(7)

            keyEncoder.encodeKey(keys, 1)
            def inline = object.encodeArray(Argument.of(List))
            inline.encodeString('a')
            inline.encodeString('b')
            inline.finishStructure()

            keyEncoder.encodeKey(keys, 2)
            def wrapped = object.encodeArray(Argument.of(List))
            wrapped.encodeString('x')
            wrapped.finishStructure()

            object.finishStructure()
        } == '<Root id="7"><item>a</item><item>b</item><entries><values>x</values></entries></Root>'
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
