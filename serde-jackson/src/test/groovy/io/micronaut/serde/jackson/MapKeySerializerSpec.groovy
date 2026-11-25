package io.micronaut.serde.jackson

/**
 * Keys with a formatted or a custom serializer are not written as the {@code toString()} of the key.
 */
class MapKeySerializerSpec extends JsonCompileSpec {

    void "a format on a map property applies to its values and leaves the integer keys as they are"() {
        given:
        def context = buildContext("test.Test", """
package test;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.micronaut.serde.annotation.Serdeable;
import java.util.Map;

@Serdeable
class Test {
    @JsonFormat(shape = JsonFormat.Shape.STRING, radix = 16)
    private Map<Integer, Integer> values;
    private Map<Integer, String> plain;
    public Map<Integer, Integer> getValues() { return values; }
    public void setValues(Map<Integer, Integer> values) { this.values = values; }
    public Map<Integer, String> getPlain() { return plain; }
    public void setPlain(Map<Integer, String> plain) { this.plain = plain; }
}
""")
        def object = typeUnderTest.type.newInstance()
        object.values = [(255): 255, (16): 16]
        object.plain = [(255): "a", (16): "b"]

        expect:
        writeJson(jsonMapper, object) == '{"values":{"255":"ff","16":"10"},"plain":{"255":"a","16":"b"}}'

        cleanup:
        context.close()
    }

    void "a key with a custom serializer is written by that serializer"() {
        given:
        def context = buildContext("test.Test", """
package test;

import io.micronaut.context.annotation.Primary;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Serdeable
class Test {
    private Map<Long, String> longs;
    private Map<UUID, String> uuids;
    private Map<Integer, String> ints;
    public Map<Long, String> getLongs() { return longs; }
    public void setLongs(Map<Long, String> longs) { this.longs = longs; }
    public Map<UUID, String> getUuids() { return uuids; }
    public void setUuids(Map<UUID, String> uuids) { this.uuids = uuids; }
    public Map<Integer, String> getInts() { return ints; }
    public void setInts(Map<Integer, String> ints) { this.ints = ints; }
}

@Singleton
@Primary
class PrefixedLongSerializer implements Serializer<Long> {
    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Long> type, Long value) throws IOException {
        encoder.encodeString("long-" + value);
    }
}

@Singleton
@Primary
class UpperCaseUuidSerializer implements Serializer<UUID> {
    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends UUID> type, UUID value) throws IOException {
        encoder.encodeString(value.toString().toUpperCase());
    }
}
""")
        def object = typeUnderTest.type.newInstance()
        object.longs = [(1L): "a"]
        object.uuids = [(UUID.fromString("5a1c8f6e-0b1d-4c3e-9f7a-2d4b6c8e0a12")): "b"]
        object.ints = [(1): "c"]

        expect:
        writeJson(jsonMapper, object) == '{"longs":{"long-1":"a"},"uuids":{"5A1C8F6E-0B1D-4C3E-9F7A-2D4B6C8E0A12":"b"},"ints":{"1":"c"}}'

        cleanup:
        context.close()
    }
}
