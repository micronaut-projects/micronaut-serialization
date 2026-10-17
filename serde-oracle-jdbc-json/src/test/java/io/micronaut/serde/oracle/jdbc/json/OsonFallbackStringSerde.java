package io.micronaut.serde.oracle.jdbc.json;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serde;
import io.micronaut.serde.oracle.jdbc.json.serde.AbstractOracleJsonSerde;

import java.io.IOException;

/**
 * An Oracle serde that does not override the native decoder hook, so the default fallback is used.
 */
public class OsonFallbackStringSerde extends AbstractOracleJsonSerde<String> {

    private static final Serde<String> DEFAULT = new Serde<>() {
        @Override
        public String deserialize(Decoder decoder, DecoderContext context, Argument<? super String> type) throws IOException {
            return "default:" + decoder.decodeString();
        }

        @Override
        public void serialize(Encoder encoder, EncoderContext context, Argument<? extends String> type, String value) throws IOException {
            encoder.encodeString(value);
        }
    };

    @Override
    protected String doDeserializeNonNull(OracleJdbcJsonParserDecoder decoder, DecoderContext decoderContext, Argument<? super String> type) throws IOException {
        return "parser:" + decoder.decodeString();
    }

    @Override
    protected void doSerializeNonNull(OracleJdbcJsonGeneratorEncoder encoder, EncoderContext context, Argument<? extends String> type, String value) {
        encoder.encodeString(value);
    }

    @Override
    protected Serde<String> getDefault() {
        return DEFAULT;
    }
}
