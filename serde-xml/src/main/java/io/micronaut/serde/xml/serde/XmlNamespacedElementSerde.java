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
package io.micronaut.serde.xml.serde;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.util.PropertySpecificSerde;
import io.micronaut.serde.xml.XmlGenerator;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

/**
 * XML serde for scalar properties serialized as a namespaced element. Only the serialize side is
 * used (the deserialize side is inherited from {@link XmlSerde}); the namespaced element name is
 * bound per property via {@link #forProperty(PropertySpecificSerde.PropertyConfiguration)}.
 */
public class XmlNamespacedElementSerde extends XmlSerde<Object> implements PropertySpecificSerde<Object> {

    private final @Nullable String localName;
    private final @Nullable String namespace;

    public XmlNamespacedElementSerde() {
        this(null, null);
    }

    private XmlNamespacedElementSerde(@Nullable String localName, @Nullable String namespace) {
        this.localName = localName;
        this.namespace = namespace;
    }

    @Override
    public @NonNull XmlNamespacedElementSerde forProperty(PropertySpecificSerde.PropertyConfiguration configuration) {
        return new XmlNamespacedElementSerde(configuration.name(), configuration.xmlNamespace());
    }

    @Override
    protected void doSerialize(XmlGenerator encoder, EncoderContext context, Object value, Argument<?> key) throws IOException {
        if (value == null) {
            encoder.encodeNull();
            return;
        }
        if (localName == null) {
            throw new SerdeException("XmlNamespacedElementSerde was not configured for: " + key);
        }
        encoder.writeNamespacedScalarForCurrentKey(localName, namespace, String.valueOf(value));
    }
}
