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
package io.micronaut.serde.util;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Encoder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;

/**
 * Common implementations for reading/writing byte arrays.
 */
@Internal
public final class BinaryCodecUtil {
    private static final Argument<byte[]> BYTE_ARRAY = Argument.of(byte[].class);

    private BinaryCodecUtil() {
    }

    public static byte[] decodeFromArray(Decoder base) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (Decoder arrayDecoder = base.decodeArray(BYTE_ARRAY)) {
            while (arrayDecoder.hasNextArrayValue()) {
                Byte b = arrayDecoder.decodeByteNullable();
                buffer.write(b == null ? 0 : b);
            }
        }
        return buffer.toByteArray();
    }

    public static byte[] decodeFromString(Decoder base) throws IOException {
        String s = base.decodeString();
        try {
            return Base64.getDecoder().decode(s);
        } catch (IllegalArgumentException e) {
            throw base.createDeserializationException("Illegal base64 input: " + e.getMessage(), null);
        }
    }

    public static void encodeToArray(Encoder encoder, byte[] data) throws IOException {
        try (Encoder arrayEncoder = encoder.encodeArray(BYTE_ARRAY)) {
            for (byte i : data) {
                arrayEncoder.encodeByte(i);
            }
        }
    }

    public static void encodeToString(Encoder encoder, byte[] data) throws IOException {
        encoder.encodeString(Base64.getEncoder().encodeToString(data));
    }
}
