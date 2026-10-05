package io.micronaut.serde.jackson

import com.fasterxml.jackson.annotation.JsonIdentityInfo
import com.fasterxml.jackson.annotation.ObjectIdGenerators
import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.Encoder
import io.micronaut.serde.Serializer
import io.micronaut.serde.annotation.Serdeable
import jakarta.inject.Singleton
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class StreamWriterSpec extends Specification {

    void "stream writer writes the bytes of writeValue per value, pretty=#pretty"() {
        given:
            def ctx = ApplicationContext.run(["micronaut.serde.jackson.pretty-print": pretty])
            def mapper = ctx.getBean(JsonMapper)
            def type = Argument.of(StreamItem)

        expect:
            mapper instanceof JacksonJsonMapper
            write(mapper, type, items) == writeEach(mapper, type, items)

        cleanup:
            ctx.close()

        where:
            [pretty, items] << [
                    [false, true],
                    [
                            [],
                            [new StreamItem("a", 1)],
                            [new StreamItem("a", 1), null, new StreamItem("b\" ", 2)],
                            (1..100).collect { new StreamItem("item" + it, it) }
                    ]
            ].combinations()
    }

    void "stream writer emits nothing between values"() {
        given:
            def ctx = ApplicationContext.run()
            def mapper = ctx.getBean(JsonMapper)

        expect:
            write(mapper, Argument.of(StreamItem), [new StreamItem("a", 1), null, new StreamItem("b", 2)]) ==
                    '{"name":"a","value":1}null{"name":"b","value":2}'

        cleanup:
            ctx.close()
    }

    void "stream writer writes each value completely before write returns"() {
        given:
            def ctx = ApplicationContext.run()
            def mapper = ctx.getBean(JsonMapper)
            def out = new ByteArrayOutputStream()
            def writer = mapper.createStreamWriter(out, Argument.of(StreamItem))

        when:
            writer.write(new StreamItem("a", 1))

        then:
            out.toString(StandardCharsets.UTF_8) == '{"name":"a","value":1}'

        when:
            writer.write(new StreamItem("b", 2))

        then:
            out.toString(StandardCharsets.UTF_8) == '{"name":"a","value":1}{"name":"b","value":2}'

        cleanup:
            writer?.close()
            ctx.close()
    }

    void "stream writer uses custom serializers"() {
        given:
            def ctx = ApplicationContext.run()
            def mapper = ctx.getBean(JsonMapper)
            def type = Argument.of(StreamCustom)
            def items = [new StreamCustom("a"), new StreamCustom("b")]

        expect:
            write(mapper, type, items) == '"A""B"'
            write(mapper, type, items) == writeEach(mapper, type, items)

        cleanup:
            ctx.close()
    }

    void "object identities do not carry over to the next value"() {
        given:
            def ctx = ApplicationContext.run()
            def mapper = ctx.getBean(JsonMapper)
            def type = Argument.of(StreamIdentified)
            def shared = new StreamIdentified(id: 1, name: "shared")
            def items = [shared, shared]

        expect:
            write(mapper, type, items) == writeEach(mapper, type, items)
            write(mapper, type, items) == '{"id":1,"name":"shared"}{"id":1,"name":"shared"}'

        cleanup:
            ctx.close()
    }

    void "closing the writer closes the stream"() {
        given:
            def ctx = ApplicationContext.run()
            def mapper = ctx.getBean(JsonMapper)
            boolean closed = false
            def out = new ByteArrayOutputStream() {
                @Override
                void close() throws IOException {
                    closed = true
                }
            }
            def writer = mapper.createStreamWriter(out, Argument.of(StreamItem))

        when:
            writer.write(new StreamItem("a", 1))

        then:
            !closed

        when:
            writer.close()

        then:
            closed
            out.toString(StandardCharsets.UTF_8) == '{"name":"a","value":1}'

        cleanup:
            ctx.close()
    }

    private static <T> String write(JsonMapper mapper, Argument<T> type, List<T> items) {
        def out = new ByteArrayOutputStream()
        try (def writer = mapper.createStreamWriter(out, type)) {
            for (T item : items) {
                writer.write(item)
            }
        }
        return out.toString(StandardCharsets.UTF_8)
    }

    // what the default JsonMapper.createStreamWriter writes
    private static <T> String writeEach(JsonMapper mapper, Argument<T> type, List<T> items) {
        def out = new ByteArrayOutputStream()
        for (T item : items) {
            mapper.writeValue(out, type, item)
        }
        return out.toString(StandardCharsets.UTF_8)
    }
}

@Serdeable
record StreamItem(String name, int value) {
}

record StreamCustom(String name) {
}

@Singleton
class StreamCustomSerializer implements Serializer<StreamCustom> {
    @Override
    void serialize(Encoder encoder, Serializer.EncoderContext context, Argument<? extends StreamCustom> type, StreamCustom value) throws IOException {
        encoder.encodeString(value.name().toUpperCase())
    }
}

@Serdeable
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator, property = "id")
class StreamIdentified {
    Integer id
    String name
}
