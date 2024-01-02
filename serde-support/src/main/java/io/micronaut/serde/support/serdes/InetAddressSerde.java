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
package io.micronaut.serde.support.serdes;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serde;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.exceptions.SerdeException;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.net.InetAddress;

/**
 * {@link Serde} implementation of {@link InetAddress}.
 * This is a based on `com.fasterxml.jackson.databind.ser.std.InetAddressSerializer` which is licenced under the Apache 2.0 licence.
 */
@Singleton
public class InetAddressSerde implements Serde<InetAddress> {

    private final boolean asNumeric;

    public InetAddressSerde(SerdeConfiguration serdeConfiguration) {
        this.asNumeric = serdeConfiguration.isInetAddressAsNumeric();
    }

    @Override
    public @NonNull Deserializer<InetAddress> createSpecific(@NonNull DecoderContext context, @NonNull Argument<? super InetAddress> type) throws SerdeException {
        return new InetAddressSerde(context.getSerdeConfiguration());
    }

    @Override
    public @NonNull Serializer<InetAddress> createSpecific(@NonNull EncoderContext context, @NonNull Argument<? extends InetAddress> type) throws SerdeException {
        return new InetAddressSerde(context.getSerdeConfiguration());
    }

    @Override
    public @Nullable InetAddress deserialize(@NonNull Decoder decoder, @NonNull DecoderContext context, @NonNull Argument<? super InetAddress> type) throws IOException {
        return InetAddress.getByName(decoder.decodeString());
    }

    @Override
    public void serialize(@NonNull Encoder encoder, @NonNull EncoderContext context, @NonNull Argument<? extends InetAddress> type, @NonNull InetAddress value) throws IOException {
        String str;
        if (asNumeric) {
            str = value.getHostAddress();
        } else {
            str = value.toString().trim();
            int ix = str.indexOf('/');
            if (ix >= 0) {
                if (ix == 0) { // missing host name; use address
                    str = str.substring(1);
                } else { // otherwise use name
                    str = str.substring(0, ix);
                }
            }
        }
        encoder.encodeString(str);
    }
}
