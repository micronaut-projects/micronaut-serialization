/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.serde.support.util;

import io.micronaut.core.annotation.Internal;

/**
 * Internal marker for serdes that map directly to one scalar value method.
 *
 * @author Denis Stepanov
 * @since 3.1
 */
@Internal
public enum DecoderValueKind {

    /**
     * No direct decoder scalar method.
     */
    NONE((byte) 0),

    /**
     * A string value.
     */
    STRING((byte) 1),

    /**
     * A boolean value.
     */
    BOOLEAN((byte) 2),

    /**
     * An {@code int} value.
     */
    INT((byte) 3),

    /**
     * A {@code long} value.
     */
    LONG((byte) 4),

    /**
     * A {@code float} value.
     */
    FLOAT((byte) 5),

    /**
     * A {@code double} value.
     */
    DOUBLE((byte) 6),

    /**
     * A {@code byte} value.
     */
    BYTE((byte) 7),

    /**
     * A {@code short} value.
     */
    SHORT((byte) 8),

    /**
     * A {@code char} value.
     */
    CHAR((byte) 9);

    public static final byte NONE_CODE = 0;
    public static final byte STRING_CODE = 1;
    public static final byte BOOLEAN_CODE = 2;
    public static final byte INT_CODE = 3;
    public static final byte LONG_CODE = 4;
    public static final byte FLOAT_CODE = 5;
    public static final byte DOUBLE_CODE = 6;
    public static final byte BYTE_CODE = 7;
    public static final byte SHORT_CODE = 8;
    public static final byte CHAR_CODE = 9;

    private final byte code;

    DecoderValueKind(byte code) {
        this.code = code;
    }

    /**
     * Returns a compact scalar kind code for hot-path storage.
     *
     * @return The scalar kind code
     */
    public byte code() {
        return code;
    }

    /**
     * Internal marker for serdes that expose the direct scalar value kind.
     *
     * @since 3.1
     */
    @Internal
    public interface Provider {

        /**
         * Returns the decoder value kind.
         *
         * @return The decoder value kind
         */
        DecoderValueKind decoderValueKind();
    }
}
