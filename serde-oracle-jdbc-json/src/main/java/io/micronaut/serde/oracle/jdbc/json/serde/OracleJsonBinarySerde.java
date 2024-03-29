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

import io.micronaut.context.annotation.Secondary;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Order;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Serde;
import io.micronaut.serde.oracle.jdbc.json.OracleJdbcJsonGeneratorEncoder;
import io.micronaut.serde.oracle.jdbc.json.OracleJdbcJsonParserDecoder;
import jakarta.inject.Singleton;
import oracle.jdbc.driver.json.tree.OracleJsonBinaryImpl;


/**
 * The custom serde for binary values for Oracle JSON.
 *
 * @author radovanradic
 * @since 2.0.0
 */
@Singleton
@Order(-100)
public class OracleJsonBinarySerde extends AbstractOracleJsonSerde<byte[]> {
    private final Serde<byte[]> byteArraySerde;

    public OracleJsonBinarySerde(@Secondary Serde<byte[]> byteArraySerde) {
        this.byteArraySerde = byteArraySerde;
    }

    @Override
    protected byte @NonNull [] doDeserializeNonNull(@NonNull OracleJdbcJsonParserDecoder decoder, @NonNull DecoderContext decoderContext,
                                                    @NonNull Argument<? super byte[]> type) {
        return decoder.decodeBinary();
    }

    @Override
    protected void doSerializeNonNull(@NonNull OracleJdbcJsonGeneratorEncoder encoder, @NonNull EncoderContext context,
                                      @NonNull Argument<? extends byte[]> type, @NonNull byte[] value) {
        encoder.encodeString(OracleJsonBinaryImpl.getString(value, false));
    }

    @Override
    protected Serde<byte[]> getDefault() {
        return byteArraySerde;
    }

}
