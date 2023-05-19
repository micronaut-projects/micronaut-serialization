package io.micronaut.serde.jackson

import io.micronaut.core.annotation.NonNull
import io.micronaut.core.annotation.Order
import io.micronaut.core.type.Argument
import io.micronaut.core.util.StringUtils
import io.micronaut.serde.Decoder
import io.micronaut.serde.Deserializer
import io.micronaut.serde.Encoder
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.Serde
import io.micronaut.serde.Serializer
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class CustomStringSerdeSpec extends Specification {

    @Inject ObjectMapper objectMapper

    void "test custom string encoder"() {
        expect:
        objectMapper.writeValueAsString(new Test(name: "Fred")) == '{"name":"derF"}'
        objectMapper.writeValueAsString(new Test(name: "")) == '{"name":null}'
        objectMapper.writeValueAsString(new Test(name: "   ")) == '{"name":null}'
        objectMapper.readValue('{"name":"derF"}', Test).name == "Fred"

    }

    @Serdeable
    static class Test {
        String name
    }

    @MockBean
    Serde<String> stringSerde() {
        return new Serde<String>() {
            @Override
            String deserialize(@NonNull Decoder decoder, @NonNull Deserializer.DecoderContext context, @NonNull Argument<? super String> type) throws IOException {
                if (decoder.decodeNull()) {
                    return null
                } else {
                    return decoder.decodeString().reverse()
                }
            }

            @Override
            void serialize(@NonNull Encoder encoder, @NonNull Serializer.EncoderContext context, @NonNull Argument<? extends String> type, @NonNull String value) throws IOException {
                if (StringUtils.isEmpty(value.trim())) {
                    encoder.encodeNull()
                } else {
                    encoder.encodeString(value.reverse())
                }
            }
        }
    }
}
