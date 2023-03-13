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
import io.micronaut.core.annotation.Order;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.oracle.jdbc.json.OracleJdbcJsonParserDecoder;
import jakarta.inject.Singleton;
import oracle.jdbc.driver.json.tree.OracleJsonBinaryImpl;

import java.io.IOException;

/**
 * Custom Oracle JSON deserializer for binary data that are represented as base16 encoded strings.
 * This deserializer can be used for metadata etag field for example.
 *
 * @author radovanradic
 * @since 2.0.0
 */
@Order(Ordered.LOWEST_PRECEDENCE)
@Singleton
public class OracleJsonBinaryStringSerde extends AbstractOracleJsonDeserializer<String> {

    @Override
    @NonNull
    protected String doDeserializeNonNull(@NonNull OracleJdbcJsonParserDecoder decoder, @NonNull DecoderContext decoderContext,
                                          @NonNull Argument<? super String> type) {
        byte[] bytes = decoder.decodeBinary();
        return OracleJsonBinaryImpl.getString(bytes, false);
    }

    @Override
    protected void doSerializeNonNull(@NonNull Encoder encoder, @NonNull EncoderContext context,
                                      @NonNull Argument<? extends String> type, @NonNull String value) throws IOException {
        encoder.encodeString(value);
    }

}
