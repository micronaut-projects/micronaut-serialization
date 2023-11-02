package io.micronaut.serde.jackson

import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Secondary
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.type.Argument
import io.micronaut.serde.*
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class CustomPrimitiveSerdeSpec extends Specification {

    @Inject ObjectMapper objectMapper

    void "test custom primitive type encoder"() {
        expect:
        objectMapper.writeValueAsString(new TestWithBoxedInt(value: 1)) == '{"value":201}'
        objectMapper.writeValueAsString(new TestWithPrimitiveInt(value: 1)) == '{"value":201}'
        objectMapper.readValue('{"value":1}', TestWithBoxedInt).value == 101
        objectMapper.readValue('{"value":1}', TestWithPrimitiveInt).value == 101
    }

    void "test custom primitive type encoder when multiple are defined"() {
        expect:
        objectMapper.writeValueAsString(new TestWithBoxedLong(value: 1L)) == '{"value":201}'
        objectMapper.writeValueAsString(new TestWithPrimitiveLong(value: 1L)) == '{"value":201}'
        objectMapper.readValue('{"value":1}', TestWithBoxedLong).value == 101L
        objectMapper.readValue('{"value":1}', TestWithPrimitiveLong).value == 101L
    }

    @Serdeable
    static class TestWithBoxedInt {
        Integer value
    }

    @Serdeable
    static class TestWithPrimitiveInt {
        int value
    }

    @Serdeable
    static class TestWithBoxedLong {
        Long value
    }

    @Serdeable
    static class TestWithPrimitiveLong {
        long value
    }

    @MockBean
    Serde<Integer> integerSerde() {
        return new Serde<Integer>() {
            @Override
            Integer deserialize(@NonNull Decoder decoder, @NonNull Deserializer.DecoderContext context, @NonNull Argument<? super Integer> type) throws IOException {
                if (decoder.decodeNull()) {
                    return null
                } else {
                    return decoder.decodeInt() + 100
                }
            }

            @Override
            void serialize(@NonNull Encoder encoder, @NonNull Serializer.EncoderContext context, @NonNull Argument<? extends Integer> type, @NonNull Integer value) throws IOException {
                if (value == null) {
                    encoder.encodeNull()
                } else {
                    encoder.encodeInt(value + 200)
                }
            }
        }
    }

    @MockBean
    @Primary
    Serde<Long> longSerdePrimary() {
        return new Serde<Long>() {
            @Override
            Long deserialize(@NonNull Decoder decoder, @NonNull Deserializer.DecoderContext context, @NonNull Argument<? super Long> type) throws IOException {
                if (decoder.decodeNull()) {
                    return null
                } else {
                    return decoder.decodeInt() + 100
                }
            }

            @Override
            void serialize(@NonNull Encoder encoder, @NonNull Serializer.EncoderContext context, @NonNull Argument<? extends Long> type, @NonNull Long value) throws IOException {
                if (value == null) {
                    encoder.encodeNull()
                } else {
                    encoder.encodeLong(value + 200)
                }
            }
        }
    }

    @MockBean
    @Secondary
    Serde<Long> longSerdeSecondary() {
        return new Serde<Long>() {
            @Override
            Long deserialize(@NonNull Decoder decoder, @NonNull Deserializer.DecoderContext context, @NonNull Argument<? super Long> type) throws IOException {
                if (decoder.decodeNull()) {
                    return null
                } else {
                    return decoder.decodeLong() + 150
                }
            }

            @Override
            void serialize(@NonNull Encoder encoder, @NonNull Serializer.EncoderContext context, @NonNull Argument<? extends Long> type, @NonNull Long value) throws IOException {
                if (value == null) {
                    encoder.encodeNull()
                } else {
                    encoder.encodeLong(value + 250)
                }
            }
        }
    }
}
