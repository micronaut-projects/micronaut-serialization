package io.micronaut.serde.xml

import io.micronaut.core.type.Argument
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.inject.Named
import spock.lang.Specification

@MicronautTest
class XmlOutputBufferSpec extends Specification {

    @Inject
    @Named(XmlObjectMapper.XML_MAPPER_NAME)
    XmlObjectMapper xmlMapper

    def "a buffer without a target grows to hold the document"() {
        given:
        def buffer = new XmlOutputBuffer()
        def bytes = (0..<3000).collect { (byte) (it % 100) } as byte[]

        when:
        buffer.write(65)
        buffer.write(bytes, 0, bytes.length)
        buffer.write(66)

        then:
        buffer.toByteArray() == ([(byte) 65] + bytes.toList() + [(byte) 66]) as byte[]
    }

    def "a buffer with a target passes on its content when full and when flushed"() {
        given:
        def target = new ByteArrayOutputStream()
        def buffer = new XmlOutputBuffer(target)
        def large = (0..<(XmlOutputBuffer.TARGET_BUFFER_SIZE * 2 + 10)).collect { (byte) (it % 100) } as byte[]

        when:
        (0..<XmlOutputBuffer.TARGET_BUFFER_SIZE).each { buffer.write(1) }

        then:
        target.size() == 0

        when:
        buffer.write(2)

        then:
        target.size() == XmlOutputBuffer.TARGET_BUFFER_SIZE

        when:
        buffer.write(large, 0, large.length)
        buffer.flush()

        then:
        target.size() == XmlOutputBuffer.TARGET_BUFFER_SIZE + 1 + large.length
        target.toByteArray()[XmlOutputBuffer.TARGET_BUFFER_SIZE] == (byte) 2
        target.toByteArray()[(XmlOutputBuffer.TARGET_BUFFER_SIZE + 1)..-1] == large.toList()
    }

    def "a document larger than the buffer is written to a stream as to bytes"() {
        given:
        def type = Argument.listOf(String)
        def value = (0..<2000).collect { "value-é-" + it }
        def stream = new ByteArrayOutputStream()

        when:
        xmlMapper.writeValue(stream, type, value)
        def bytes = xmlMapper.writeValueAsBytes(type, value)

        then:
        stream.size() > XmlOutputBuffer.TARGET_BUFFER_SIZE
        stream.toByteArray() == bytes
        xmlMapper.readValue(bytes, type) == value
    }

    def "the root element is named after the argument"() {
        expect:
        xmlMapper.writeValueAsString(Argument.of(String, 'greeting'), 'hi') == '<Greeting>hi</Greeting>'
        xmlMapper.writeValueAsString(Argument.of(String, 'greeting'), 'hi') == '<Greeting>hi</Greeting>'
        xmlMapper.writeValueAsString(Argument.of(String, 'other_name'), 'hi') == '<OtherName>hi</OtherName>'
        xmlMapper.writeValueAsString('hi') == '<String>hi</String>'
    }
}
