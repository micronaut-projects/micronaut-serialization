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

    void "a buffer writes nothing more once its stream failed"() {
        given:
        def target = new FailOnceStream(3)
        def buffer = new YamlOutputBuffer(target)
        buffer.write('abcdef')

        when:
        buffer.flush()

        then:
        thrown(UncheckedIOException)

        when:
        buffer.write('g')
        buffer.flush()

        then:
        thrown(UncheckedIOException)
        target.beforeFailure.toString('UTF-8') == 'abc'
        target.afterFailure.size() == 0
    }

    void "a failed document is not written again when the emitter finishes it"() {
        given:
        def mapper = context.getBean(YamlObjectMapper)
        def target = new FailOnceStream(4)

        when:
        mapper.writeValue(target, Argument.listOf(String), ['a', 'b', 'c'])

        then:
        thrown(Exception)
        target.afterFailure.size() == 0
    }

    void "a large piece is encoded in blocks that keep surrogate pairs whole"() {
        given:
        def target = new ByteArrayOutputStream()
        def buffer = new YamlOutputBuffer(target)
        // the pair straddles the end of the first block
        def large = 'a' * (YamlOutputBuffer.BLOCK_SIZE - 1) + '\uD83D\uDE00' + 'b' * (YamlOutputBuffer.BLOCK_SIZE * 2)

        when:
        buffer.write('x')
        buffer.write(large)
        buffer.flush()

        then:
        target.toString('UTF-8') == 'x' + large
        buffer.@pending.capacity() < YamlOutputBuffer.BLOCK_SIZE * 2
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
