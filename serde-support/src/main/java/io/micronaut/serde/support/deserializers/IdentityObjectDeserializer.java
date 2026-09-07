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
package io.micronaut.serde.support.deserializers;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.UpdatingDeserializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.util.DocumentIdUtil;
import io.micronaut.serde.support.util.JsonNodeDecoder;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

/**
 * Deserializes a bean whose document-scoped identifier carries object identity semantics ({@code @JsonIdentityInfo}):
 * an object value is read by the delegate, a scalar value is a reference to a bean already read in the current document.
 *
 * @since 3.2
 */
@Internal
final class IdentityObjectDeserializer implements UpdatingDeserializer<Object> {
    private final Deserializer<Object> delegate;
    private final @Nullable UpdatingDeserializer<Object> updatingDelegate;

    IdentityObjectDeserializer(Deserializer<Object> delegate) {
        this.delegate = delegate;
        this.updatingDelegate = delegate instanceof UpdatingDeserializer<Object> updatingDeserializer ? updatingDeserializer : null;
    }

    @Override
    public Object deserialize(Decoder decoder, DecoderContext context, Argument<? super Object> type) throws IOException {
        JsonNode node = decoder.decodeNode();
        Object id = node.isValueNode() ? node.getValue() : null;
        if (id == null) {
            LimitingStream.RemainingLimits limits = context.getSerdeConfiguration()
                .map(LimitingStream::limitsFromConfiguration)
                .orElse(LimitingStream.DEFAULT_LIMITS);
            return delegate.deserialize(JsonNodeDecoder.create(node, limits), context, type);
        }
        Object value = DocumentIdUtil.resolve(context, id, type);
        if (value == null) {
            throw new SerdeException("Unresolved identifier reference [" + id + "] for type [" + type.getType().getName() + "]");
        }
        return value;
    }

    @Override
    public void deserializeInto(Decoder decoder, DecoderContext context, Argument<? super Object> type, Object value) throws IOException {
        if (updatingDelegate == null) {
            throw new SerdeException("Updating existing value of type [" + type + "] is not supported");
        }
        updatingDelegate.deserializeInto(decoder, context, type, value);
    }
}
