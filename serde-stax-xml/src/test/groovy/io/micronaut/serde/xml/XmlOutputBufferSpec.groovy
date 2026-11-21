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

    def "a buffer writes nothing more once its target failed"() {
        given:
        def target = new FailOnceStream(10)
        def buffer = new XmlOutputBuffer(target)
        def large = new byte[XmlOutputBuffer.TARGET_BUFFER_SIZE + 1]

        when:
        (0..<100).each { buffer.write(1) }
        buffer.flush()

        then:
        thrown(IOException)

        when:
        buffer.write(2)
        buffer.flush()

        then:
        thrown(IOException)

        when:
        buffer.write(large, 0, large.length)

        then:
        thrown(IOException)
        target.beforeFailure.size() == 10
        target.afterFailure.size() == 0
    }

    def "a failed write of a large scalar is not written again when the writer is closed"() {
        given:
        def target = new FailOnceStream(100)

        when:
        xmlMapper.writeValue(target, Argument.of(String, 'greeting'), 'x' * (XmlOutputBuffer.TARGET_BUFFER_SIZE * 2))

        then:
        thrown(IOException)
        target.afterFailure.size() == 0
    }

    /**
     * Takes the first bytes of the write that fails, fails once, then records what is written after the failure.
     */
    static class FailOnceStream extends OutputStream {
        final int failAt
        final ByteArrayOutputStream beforeFailure = new ByteArrayOutputStream()
        final ByteArrayOutputStream afterFailure = new ByteArrayOutputStream()
        boolean failed

        FailOnceStream(int failAt) {
            this.failAt = failAt
        }

        @Override
        void write(int b) throws IOException {
            write([(byte) b] as byte[], 0, 1)
        }

        @Override
        void write(byte[] b, int off, int len) throws IOException {
            if (failed) {
                afterFailure.write(b, off, len)
                return
            }
            int room = failAt - beforeFailure.size()
            if (len <= room) {
                beforeFailure.write(b, off, len)
                return
            }
            beforeFailure.write(b, off, room)
            failed = true
            throw new IOException('broken')
        }
    }
}
