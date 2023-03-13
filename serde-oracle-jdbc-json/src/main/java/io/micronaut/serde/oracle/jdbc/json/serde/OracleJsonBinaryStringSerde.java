/*
 * Copyright 2017-2023 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.serde.oracle.jdbc.json.serde;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.oracle.jdbc.json.OracleJdbcJsonGeneratorEncoder;
import io.micronaut.serde.oracle.jdbc.json.OracleJdbcJsonParserDecoder;
import io.micronaut.serde.oracle.jdbc.json.annotation.OracleType;
import io.micronaut.serde.support.DefaultSerdeRegistry;
import io.micronaut.serde.util.NullableSerde;
import jakarta.inject.Singleton;
import oracle.jdbc.driver.json.tree.OracleJsonBinaryImpl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Custom Oracle JSON deserializer for binary data that are represented as base16 encoded strings.
 * This deserializer can be used for metadata etag field for example.
 *
 * @author radovanradic
 * @since 2.0.0
 */
@Singleton
public class OracleJsonBinaryStringSerde extends AbstractOracleJsonSerde<String> {

    @Override
    @NonNull
    protected String doDeserializeNonNull(@NonNull OracleJdbcJsonParserDecoder decoder, @NonNull DecoderContext decoderContext,
                                          @NonNull Argument<? super String> type) throws IOException {
        OracleType.Type t = type.getAnnotationMetadata().enumValue(OracleType.class, OracleType.Type.class).orElse(null);
        if (t == OracleType.Type.BINARY || t == OracleType.Type.BASE16_STRING) {
            byte[] bytes = decoder.decodeBinary();
            return OracleJsonBinaryImpl.getString(bytes, false);
        } else {
            return getDefault().deserializeNonNull(
                decoder,
                decoderContext,
                type
            );
        }
    }

    @Override
    protected void doSerializeNonNull(@NonNull OracleJdbcJsonGeneratorEncoder encoder, @NonNull EncoderContext context,
                                      @NonNull Argument<? extends String> type, @NonNull String value) throws IOException {
        OracleType.Type t = type.getAnnotationMetadata().enumValue(OracleType.class, OracleType.Type.class).orElse(null);
        if (t == OracleType.Type.BASE16_STRING) {
            encoder.encodeString(value);
        } else if (t == OracleType.Type.BINARY) {
            encoder.encodeValue(new OracleJsonBinaryImpl(value.getBytes(StandardCharsets.UTF_8), false));
        } else {
            getDefault().serialize(
                encoder,
                context,
                type,
                value
            );
        }
    }

    @Override
    protected NullableSerde<String> getDefault() {
        return DefaultSerdeRegistry.STRING_SERDE;
    }

}
