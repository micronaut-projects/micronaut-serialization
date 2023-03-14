package io.micronaut.serde.bson;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.oracle.jdbc.json.OracleJdbcJsonParserDecoder;
import io.micronaut.serde.util.NullableDeserializer;
import jakarta.inject.Singleton;
import oracle.jdbc.driver.json.tree.OracleJsonBinaryImpl;

import java.io.IOException;

@Singleton
public class MetadataDeserializer implements NullableDeserializer<Metadata> {

    private static final String ETAG_FIELD = "etag";
    private static final String ASOF_FIELD = "asof";

    @Override
    public Metadata deserializeNonNull(Decoder decoder, DecoderContext decoderContext, Argument<? super Metadata> type) throws IOException {
        Metadata metadata = new Metadata();
        if (decoder instanceof OracleJdbcJsonParserDecoder oracleJdbcJsonParserDecoder) {
            OracleJdbcJsonParserDecoder objDecoder = (OracleJdbcJsonParserDecoder) oracleJdbcJsonParserDecoder.decodeObject();
            String key = objDecoder.decodeKey();
            byte[] value = objDecoder.decodeBinary();
            setField(metadata, key, value);
            key = objDecoder.decodeKey();
            value = objDecoder.decodeBinary();
            setField(metadata, key, value);
            objDecoder.finishStructure();
        } else {
            Decoder objDecoder = decoder.decodeObject();
            String key = objDecoder.decodeKey();
            String value = objDecoder.decodeString();
            setField(metadata, key, value);
            key = objDecoder.decodeKey();
            value = objDecoder.decodeString();
            setField(metadata, key, value);
            objDecoder.finishStructure();
        }
        return metadata;
    }

    private void setField(Metadata metadata, String key, byte[] value) throws IOException {
        if (ETAG_FIELD.equals(key)) {
            metadata.setEtag(OracleJsonBinaryImpl.getString(value, false));
        } else if (ASOF_FIELD.equals(key)) {
            metadata.setAsof(OracleJsonBinaryImpl.getString(value, false));
        } else {
            throw new IOException("Unexpected metadata field " + key);
        }
    }

    private void setField(Metadata metadata, String key, String value) throws IOException {
        if (ETAG_FIELD.equals(key)) {
            metadata.setEtag(value);
        } else if (ASOF_FIELD.equals(key)) {
            metadata.setAsof(value);
        } else {
            throw new IOException("Unexpected metadata field " + key);
        }
    }
}
