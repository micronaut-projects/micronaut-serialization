/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.serde.support.deserializers;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.DelegatingDecoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Keys;
import io.micronaut.serde.KeysAwareDecoder;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.support.util.JsonNodeDecoder;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Map;

/**
 * Subtyped property deserializer.
 *
 * @author Denis Stepanov
 * @since 2.4.0
 */
@Internal
final class SubtypedPropertyObjectDeserializer implements Deserializer<Object> {

    private final DeserializerSubtypeInfo<? super Object> subtypeInfo;
    private final Keys discriminatorKeys;
    private final boolean discriminatorVisible;

    /**
     * @param subtypeInfo       The subtype info
     * @param discriminatorKeys The keys of the discriminator name. This deserializer is created per document, and
     *                          decoders cache work per {@link Keys} instance (Jackson's name matcher, the protobuf
     *                          slot table), so the registry that creates it shares one instance per name.
     */
    SubtypedPropertyObjectDeserializer(DeserializerSubtypeInfo<? super Object> subtypeInfo, Keys discriminatorKeys) {
        this.subtypeInfo = subtypeInfo;
        SerdeConfig.SerSubtyped.DiscriminatorType discriminatorType = subtypeInfo.parent().info().discriminatorType();
        if (discriminatorType != SerdeConfig.SerSubtyped.DiscriminatorType.PROPERTY
            && discriminatorType != SerdeConfig.SerSubtyped.DiscriminatorType.EXISTING_PROPERTY) {
            throw new IllegalStateException("Unsupported discriminator type: " + discriminatorType);
        }
        this.discriminatorKeys = discriminatorKeys;
        this.discriminatorVisible = subtypeInfo.parent().info().discriminatorVisible();
    }

    @Override
    public Object deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Object> type)
        throws IOException {
        if (!discriminatorVisible) {
            // Serialization writes the discriminator first. When it comes first, the subtype can read the rest of the
            // object directly, without buffering it through the demuxing decoder.
            Decoder objectDecoder = decoder.decodeObject(type);
            if (objectDecoder instanceof KeysAwareDecoder keysAware) {
                int keyIndex = keysAware.decodeKey(discriminatorKeys);
                if (keyIndex == 0) {
                    return findDeserializerForValue(keysAware).deserialize(new OpenedObjectDecoder(objectDecoder), decoderContext, type);
                }
                if (keyIndex == KeysAwareDecoder.MATCH_END_OBJECT) {
                    // an empty object, which the subtype reads as one
                    objectDecoder.finishStructure();
                    return subtypeInfo.findDeserializer(null).deserialize(emptyObjectDecoder(), decoderContext, type);
                }
                // MATCH_UNKNOWN_NAME: decodeKey() returns the same name again, so the demuxing decoder sees every key
            }
            return deserializeDemuxed(new OpenedObjectDecoder(objectDecoder), decoderContext, type);
        }
        return deserializeDemuxed(decoder, decoderContext, type);
    }

    /**
     * A decoder over an empty object. It holds no resource, and closing it would finish a structure that the
     * subtype deserializer already finished, so it is not closed.
     */
    @SuppressWarnings("java:S2095")
    private static Decoder emptyObjectDecoder() {
        return JsonNodeDecoder.create(JsonNode.createObjectNode(Map.of()), LimitingStream.DEFAULT_LIMITS);
    }

    private Deserializer<? super Object> findDeserializerForValue(Decoder decoder) throws IOException {
        if (decoder.decodeNull()) {
            return subtypeInfo.findDeserializer(null);
        }
        return subtypeInfo.findDeserializer(decoder.decodeString());
    }

    private Object deserializeDemuxed(Decoder decoder, DecoderContext decoderContext, Argument<? super Object> type)
        throws IOException {
        try (DemuxingObjectDecoder.PrimedDecoder primed = DemuxingObjectDecoder.prime(decoder)) {
            Decoder typeFinder;
            if (discriminatorVisible) {
                typeFinder = primed.decodeObjectNonConsuming(type);
            } else {
                typeFinder = primed.decodeObject(type);
            }
            Deserializer<Object> deserializer = findDeserializer(typeFinder);
            typeFinder.finishStructure(true);

            return deserializer.deserialize(
                primed,
                decoderContext,
                type
            );
        }
    }

    private Deserializer<? super Object> findDeserializer(Decoder decoder) throws IOException {
        final KeysAwareDecoder objectDecoder = KeysAwareDecoder.of(decoder);

        while (true) {
            final int keyIndex = objectDecoder.decodeKey(discriminatorKeys);
            if (keyIndex == KeysAwareDecoder.MATCH_END_OBJECT) {
                break;
            }

            if (keyIndex == 0) {
                return findDeserializerForValue(objectDecoder);
            } else {
                objectDecoder.decodeKey();
                objectDecoder.skipValue();
            }
        }
        return subtypeInfo.findDeserializer(null);
    }

    /**
     * A decoder over an object whose start was already read. Like {@link DemuxingObjectDecoder.PrimedDecoder},
     * it only supports opening the object, which returns the decoder of the opened object.
     */
    private static final class OpenedObjectDecoder extends DelegatingDecoder {
        private final Decoder objectDecoder;

        private OpenedObjectDecoder(Decoder objectDecoder) {
            this.objectDecoder = objectDecoder;
        }

        @Override
        public Decoder decodeObject() {
            return objectDecoder;
        }

        @Override
        public Decoder decodeObject(Argument<?> type) {
            return objectDecoder;
        }

        @Override
        public boolean decodeNull() {
            return false;
        }

        @Override
        protected Decoder delegate() {
            throw new UnsupportedOperationException();
        }

        @Override
        public IOException createDeserializationException(String message, @Nullable Object invalidValue) {
            return objectDecoder.createDeserializationException(message, invalidValue);
        }

        @Override
        public void close() {
            // the subtype deserializer finishes the object
        }
    }

}
