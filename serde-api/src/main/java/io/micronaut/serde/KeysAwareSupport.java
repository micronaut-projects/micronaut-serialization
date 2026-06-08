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
import io.micronaut.core.type.Argument;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/**
 * Support logic for adapting encoders and decoders to keys-aware variants.
 *
 * @author Denis Stepanov
 * @since 3.1
 */
@Internal
final class KeysAwareSupport {

    private KeysAwareSupport() {
    }

    static KeysAwareDecoder decoder(Decoder decoder) {
        Objects.requireNonNull(decoder, "decoder");
        if (decoder instanceof KeysAwareDecoder keysAwareDecoder) {
            return keysAwareDecoder;
        }
        return new FallbackKeysAwareDecoder(decoder);
    }

    static KeysAwareEncoder encoder(Encoder encoder) {
        Objects.requireNonNull(encoder, "encoder");
        if (encoder instanceof KeysAwareEncoder keysAwareEncoder) {
            return keysAwareEncoder;
        }
        return new FallbackKeysAwareEncoder(encoder);
    }

    private static final class FallbackKeysAwareDecoder extends DelegatingDecoder implements KeysAwareDecoder {
        private final Decoder delegate;
        @Nullable
        private String pendingUnknownKey;

        private FallbackKeysAwareDecoder(Decoder delegate) {
            this.delegate = delegate;
        }

        @Override
        protected Decoder delegate() {
            pendingUnknownKey = null;
            return delegate;
        }

        @Override
        public Decoder decodeArray(Argument<?> type) throws IOException {
            pendingUnknownKey = null;
            return decoder(delegate.decodeArray(type));
        }

        @Override
        public Decoder decodeArray() throws IOException {
            pendingUnknownKey = null;
            return decoder(delegate.decodeArray());
        }

        @Override
        public Decoder decodeObject(Argument<?> type) throws IOException {
            pendingUnknownKey = null;
            return decoder(delegate.decodeObject(type));
        }

        @Override
        public Decoder decodeObject() throws IOException {
            pendingUnknownKey = null;
            return decoder(delegate.decodeObject());
        }

        @Override
        public @Nullable String decodeKey() throws IOException {
            String key = pendingUnknownKey;
            if (key != null) {
                pendingUnknownKey = null;
                return key;
            }
            return delegate.decodeKey();
        }

        @Override
        public int decodeKey(Keys keys) throws IOException {
            if (pendingUnknownKey != null) {
                return MATCH_UNKNOWN_NAME;
            }
            String key = delegate.decodeKey();
            if (key == null) {
                return MATCH_END_OBJECT;
            }
            int keyIndex = keys.indexOf(key);
            if (keyIndex == Keys.UNKNOWN_KEY) {
                // MATCH_UNKNOWN_NAME means this key did not match the supplied Keys.
                // Keep it pending so decodeKey() can expose the same unknown name.
                pendingUnknownKey = key;
                return MATCH_UNKNOWN_NAME;
            }
            return keyIndex;
        }

        @Override
        public IOException createDeserializationException(String message, @Nullable Object invalidValue) {
            return delegate.createDeserializationException(message, invalidValue);
        }
    }

    private static final class FallbackKeysAwareEncoder implements KeysAwareEncoder {
        private final Encoder delegate;

        private FallbackKeysAwareEncoder(Encoder delegate) {
            this.delegate = delegate;
        }

        @Override
        public Encoder encodeArray(Argument<?> type) throws IOException {
            return delegate.encodeArray(type);
        }

        @Override
        public Encoder encodeObject(Argument<?> type) throws IOException {
            return encoder(delegate.encodeObject(type));
        }

        @Override
        public void finishStructure() throws IOException {
            delegate.finishStructure();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        @Override
        public void encodeKey(String key) throws IOException {
            delegate.encodeKey(key);
        }

        @Override
        public void encodeKey(Keys keys, int index) throws IOException {
            delegate.encodeKey(KeysSupport.keyAt(keys, index));
        }

        @Override
        public void encodeString(String value) throws IOException {
            delegate.encodeString(value);
        }

        @Override
        public void encodeBoolean(boolean value) throws IOException {
            delegate.encodeBoolean(value);
        }

        @Override
        public void encodeByte(byte value) throws IOException {
            delegate.encodeByte(value);
        }

        @Override
        public void encodeShort(short value) throws IOException {
            delegate.encodeShort(value);
        }

        @Override
        public void encodeChar(char value) throws IOException {
            delegate.encodeChar(value);
        }

        @Override
        public void encodeInt(int value) throws IOException {
            delegate.encodeInt(value);
        }

        @Override
        public void encodeLong(long value) throws IOException {
            delegate.encodeLong(value);
        }

        @Override
        public void encodeFloat(float value) throws IOException {
            delegate.encodeFloat(value);
        }

        @Override
        public void encodeDouble(double value) throws IOException {
            delegate.encodeDouble(value);
        }

        @Override
        public void encodeBigInteger(BigInteger value) throws IOException {
            delegate.encodeBigInteger(value);
        }

        @Override
        public void encodeBigDecimal(BigDecimal value) throws IOException {
            delegate.encodeBigDecimal(value);
        }

        @Override
        public void encodeBinary(byte[] data) throws IOException {
            delegate.encodeBinary(data);
        }

        @Override
        public void encodeNull() throws IOException {
            delegate.encodeNull();
        }

        @Override
        public String currentPath() {
            return delegate.currentPath();
        }
    }
}
