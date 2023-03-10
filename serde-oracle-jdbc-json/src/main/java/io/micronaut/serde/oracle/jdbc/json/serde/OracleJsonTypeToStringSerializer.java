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

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;

import java.io.IOException;


/**
 * The custom serializer for Oracle JSON that just returns Object#toString value.
 *
 * @author radovanradic
 * @since 2.0.0
 *
 * @param <T> the type that is to be serialized
 */
public abstract class OracleJsonTypeToStringSerializer<T> implements Serializer<T> {
    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException {
        if (value == null) {
            encoder.encodeNull();
        } else {
            encoder.encodeString(value.toString());
        }
    }
}
