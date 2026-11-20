package io.micronaut.serde.yaml

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class YamlOutputBufferSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    void "pieces are encoded in blocks and on flush"() {
        given:
        def target = new ByteArrayOutputStream()
        def buffer = new YamlOutputBuffer(target)
        def block = 'é' * YamlOutputBuffer.BLOCK_SIZE

        when:
        buffer.write('ab')
        buffer.write('xcdx', 1, 2)

        then:
        target.size() == 0

        when:
        buffer.write(block)

        then:
        target.toString('UTF-8') == 'abcd' + block

        when:
        buffer.write('z')
        buffer.flush()

        then:
        target.toString('UTF-8') == 'abcd' + block + 'z'
    }

    void "a failing stream is reported as an unchecked IO exception"() {
        given:
        def buffer = new YamlOutputBuffer(new OutputStream() {
            @Override
            void write(int b) throws IOException {
                throw new IOException('broken')
            }
        })
        buffer.write('a')

        when:
        buffer.flush()

        then:
        def e = thrown(UncheckedIOException)
        e.cause.message == 'broken'
    }

    void "a document larger than a block is written to a stream as to bytes"() {
        given:
        def mapper = context.getBean(YamlObjectMapper)
        def type = Argument.listOf(String)
        def value = (0..<2000).collect { "value-é-" + it }
        def stream = new ByteArrayOutputStream()

        when:
        mapper.writeValue(stream, type, value)
        def bytes = mapper.writeValueAsBytes(type, value)

        then:
        stream.size() > YamlOutputBuffer.BLOCK_SIZE
        stream.toByteArray() == bytes
        mapper.readValue(bytes, type) == value
    }
}
