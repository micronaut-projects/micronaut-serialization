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
package io.micronaut.serde;

import io.micronaut.core.annotation.Internal;

import java.io.IOException;

/**
 * Optional decoder capability for matching object keys against a known key set.
 *
 * @author Denis Stepanov
 * @since 3.1
 */
@Internal
public interface KeysAwareDecoder extends Decoder {

    /**
     * Marker for case where the end of an object was encountered.
     */
    int MATCH_END_OBJECT = -1;

    /**
     * Marker for case where a property name was encountered but not matched
     * by the {@link Keys} supplied to {@link #decodeKey(Keys)}. After this
     * marker is returned, callers should obtain the unmatched property name
     * with {@link #decodeKey()} and handle it as unknown for that same key set.
     * Implementations must not return this marker for a property that matches
     * the supplied keys.
     */
    int MATCH_UNKNOWN_NAME = -2;

    /**
     * Adapt the decoder to the keys-aware contract.
     *
     * @param decoder The decoder
     * @return A keys-aware decoder
     */
    static KeysAwareDecoder of(Decoder decoder) {
        return KeysAwareSupport.decoder(decoder);
    }

    /**
     * Decode the next object key and match it against the supplied key set.
     * A non-negative result is the matched key index. {@link #MATCH_UNKNOWN_NAME}
     * means the current key is available through {@link #decodeKey()} and is
     * guaranteed not to match the supplied {@link Keys}.
     *
     * @param keys The keys
     * @return The matched key index, {@link #MATCH_END_OBJECT}, or {@link #MATCH_UNKNOWN_NAME}
     * @throws IOException If an unrecoverable error occurs
     */
    int decodeKey(Keys keys) throws IOException;

    /**
     * Decode the next value if it is a string that is one of the supplied constant values, such as
     * the serialized names of the constants of an enum. A non-negative result is the index of the
     * matched value, which is then consumed. {@link #MATCH_UNKNOWN_NAME} means the value was not
     * consumed: it is not a string, is not one of the values, or the decoder does not match values,
     * and the caller decodes it as usual.
     *
     * @param values The values
     * @return The matched value index or {@link #MATCH_UNKNOWN_NAME}
     * @throws IOException If an unrecoverable error occurs
     * @since 3.3
     */
    default int decodeStringValue(Keys values) throws IOException {
        return MATCH_UNKNOWN_NAME;
    }

    /**
     * Decode the next value with any decoder if it is a string that is one of the supplied constant
     * values, see {@link #decodeStringValue(Keys)}.
     *
     * @param decoder The decoder
     * @param values The values
     * @return The matched value index or {@link #MATCH_UNKNOWN_NAME}
     * @throws IOException If an unrecoverable error occurs
     * @since 3.3
     */
    static int decodeStringValue(Decoder decoder, Keys values) throws IOException {
        if (decoder instanceof KeysAwareDecoder keysAwareDecoder) {
            return keysAwareDecoder.decodeStringValue(values);
        }
        return MATCH_UNKNOWN_NAME;
    }
}
