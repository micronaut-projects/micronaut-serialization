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
import io.micronaut.serde.Serializer;
import io.micronaut.serde.XmlElementConfigurableSerializer;
import io.micronaut.serde.xml.XmlGenerator;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

public class XmlPropertySerde extends XmlSerde<Object> implements XmlElementConfigurableSerializer<Object> {

    private final @Nullable String namespace;

    public XmlPropertySerde() {
        this(null);
    }

    private XmlPropertySerde(@Nullable String namespace) {
        this.namespace = namespace;
    }

    @Override
    public @NonNull Serializer<Object> withXmlElement(@NonNull String localName, @Nullable String namespace) {
        if (namespace == null || namespace.isEmpty()) {
            return this;
        }
        return new XmlPropertySerde(namespace);
    }

    @Override
    protected void doSerialize(XmlGenerator encoder, EncoderContext context, Object value, Argument<?> key) throws IOException {
        if (value == null) {
            encoder.encodeNull();
            return;
        }
        if (namespace != null) {
            encoder.writeNamespacedAttributeForCurrentKey(namespace, String.valueOf(value));
        } else {
            encoder.writeAttributeForCurrentKey(String.valueOf(value));
        }
    }
}
