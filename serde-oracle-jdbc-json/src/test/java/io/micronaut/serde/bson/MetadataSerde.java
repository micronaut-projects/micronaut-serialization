package io.micronaut.serde.bson;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.oracle.jdbc.json.OracleJdbcJsonGeneratorEncoder;
import io.micronaut.serde.oracle.jdbc.json.OracleJdbcJsonParserDecoder;
import io.micronaut.serde.oracle.jdbc.json.serde.AbstractOracleJsonSerde;
import io.micronaut.serde.util.NullableSerde;
import jakarta.inject.Singleton;
import oracle.jdbc.driver.json.tree.OracleJsonBinaryImpl;

import java.io.IOException;

@Singleton
public class MetadataSerde extends AbstractOracleJsonSerde<Metadata> {

    private static final String ETAG_FIELD = "etag";
    private static final String ASOF_FIELD = "asof";

    @Override
    protected Metadata doDeserializeNonNull(OracleJdbcJsonParserDecoder decoder, DecoderContext decoderContext, Argument<? super Metadata> type) throws IOException {
        Metadata metadata = new Metadata();
        OracleJdbcJsonParserDecoder objDecoder = (OracleJdbcJsonParserDecoder) decoder.decodeObject();
        String key = objDecoder.decodeKey();
        byte[] value = objDecoder.decodeBinary();
        setField(metadata, key, value);
        key = objDecoder.decodeKey();
        value = objDecoder.decodeBinary();
        setField(metadata, key, value);
        objDecoder.finishStructure();
        return metadata;
    }

    private void setField(Metadata metadata, String key, byte[] value) throws IOException {
        if (ETAG_FIELD.equals(key)) {
            metadata.setEtag(value);
        } else if (ASOF_FIELD.equals(key)) {
            metadata.setAsof(value);
        } else {
            throw new IOException("Unexpected metadata field " + key);
        }
    }

    @Override
    protected void doSerializeNonNull(OracleJdbcJsonGeneratorEncoder encoder, EncoderContext context, Argument<? extends Metadata> type, Metadata value) throws IOException {
        Encoder objEncoder = encoder.encodeObject(type);
        objEncoder.encodeKey(ETAG_FIELD);
        objEncoder.encodeString(OracleJsonBinaryImpl.getString(value.getEtag(), false));
        objEncoder.encodeKey(ASOF_FIELD);
        objEncoder.encodeString(OracleJsonBinaryImpl.getString(value.getAsof(), false));
        objEncoder.finishStructure();
    }

    @Override
    protected NullableSerde<Metadata> getDefault() {
        throw new RuntimeException("Metadata object doesn't have default serde");
    }
}
