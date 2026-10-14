package io.micronaut.serde.fory.json

import io.micronaut.core.io.buffer.ByteArrayBufferFactory
import io.micronaut.core.io.buffer.ReadBufferFactory
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@MicronautTest(startApplication = false)
class ForyJsonMapperBufferSpec extends Specification {

    private static final Argument<Map<String, Object>> MAP = Argument.mapOf(String, Object)

    @Inject
    JsonMapper jsonMapper

    private static byte[] padded(String json) {
        ("xx" + json + "yy").getBytes(StandardCharsets.UTF_8)
    }

    void "reads a heap ByteBuffer slice in place"() {
        given:
        def bytes = padded('{"a":1,"b":"x"}')
        def nio = ByteBuffer.wrap(bytes, 2, bytes.length - 4).slice()

        expect:
        jsonMapper.readValue(ByteArrayBufferFactory.INSTANCE.wrap(bytes).slice(2, bytes.length - 4), MAP) == [a: 1, b: 'x']
        jsonMapper.readValue(ReadBufferFactory.getJdkFactory().adapt(nio), MAP) == [a: 1, b: 'x']
    }

    void "reads a null root from a ReadBuffer"() {
        expect:
        jsonMapper.readValue(ReadBufferFactory.getJdkFactory().adapt('null'.getBytes(StandardCharsets.UTF_8)), MAP) == null
    }

    void "reads a direct ReadBuffer"() {
        given:
        def json = '{"a":[1,2]}'.getBytes(StandardCharsets.UTF_8)
        def direct = ByteBuffer.allocateDirect(json.length).put(json).flip()

        expect:
        jsonMapper.readValue(ReadBufferFactory.getJdkFactory().adapt(direct), MAP) == [a: [1, 2]]
    }

    void "syntax errors from a ReadBuffer are reported"() {
        when:
        jsonMapper.readValue(ReadBufferFactory.getJdkFactory().adapt('{foo}'.getBytes(StandardCharsets.UTF_8)), MAP)

        then:
        thrown(io.micronaut.json.JsonSyntaxException)
    }

    void "pooled state is safe across many virtual threads"() {
        given:
        def executor = Executors.newVirtualThreadPerTaskExecutor()

        when:
        def futures = (0..<2000).collect { int i ->
            executor.submit({
                def json = jsonMapper.writeValueAsBytes([id: i, name: "n$i".toString(), tags: ['a', 'b']])
                jsonMapper.readValue(json, MAP)
            } as Callable)
        }
        def results = futures*.get()

        then:
        results.withIndex().every { r, i -> r == [id: i, name: "n$i".toString(), tags: ['a', 'b']] }

        cleanup:
        executor.close()
    }
}
