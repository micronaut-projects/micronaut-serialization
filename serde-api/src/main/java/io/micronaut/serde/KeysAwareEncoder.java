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
 * Optional encoder capability for writing object keys from a known key set.
 *
 * @author Denis Stepanov
 * @since 3.0
 */
@Internal
public interface KeysAwareEncoder extends Encoder {

    /**
     * Adapt the encoder to the keys-aware contract.
     *
     * @param encoder The encoder
     * @return A keys-aware encoder
     */
    static KeysAwareEncoder of(Encoder encoder) {
        return KeysAwareSupport.encoder(encoder);
    }

    /**
     * Encode a key by index from the supplied key set.
     *
     * @param keys The keys
     * @param index The key index
     * @throws IOException If an unrecoverable error occurs
     */
    void encodeKey(Keys keys, int index) throws IOException;
}
