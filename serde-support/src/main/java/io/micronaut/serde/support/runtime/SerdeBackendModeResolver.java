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
package io.micronaut.serde.support.runtime;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.config.SerdeBackendMode;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.annotation.SerdeConfig;
import org.jspecify.annotations.Nullable;

@Internal
public final class SerdeBackendModeResolver {

    private final SerdeConfiguration serdeConfiguration;

    public SerdeBackendModeResolver(SerdeConfiguration serdeConfiguration) {
        this.serdeConfiguration = serdeConfiguration;
    }

    public SerdeBackendMode resolveSerializationMode(@Nullable AnnotationMetadata annotationMetadata) {
        SerdeBackendMode directional = resolve(annotationMetadata, SerdeConfig.SERIALIZE_BACKEND);
        if (directional != null) {
            return directional;
        }
        SerdeBackendMode shared = resolve(annotationMetadata, SerdeConfig.BACKEND);
        if (shared != null) {
            return shared;
        }
        return globalDefault();
    }

    public SerdeBackendMode resolveDeserializationMode(@Nullable AnnotationMetadata annotationMetadata) {
        SerdeBackendMode directional = resolve(annotationMetadata, SerdeConfig.DESERIALIZE_BACKEND);
        if (directional != null) {
            return directional;
        }
        SerdeBackendMode shared = resolve(annotationMetadata, SerdeConfig.BACKEND);
        if (shared != null) {
            return shared;
        }
        return globalDefault();
    }

    private SerdeBackendMode globalDefault() {
        SerdeBackendMode configured = serdeConfiguration.getBackendMode();
        return configured == null ? SerdeBackendMode.AUTO : configured;
    }

    private @Nullable SerdeBackendMode resolve(@Nullable AnnotationMetadata annotationMetadata, String member) {
        if (annotationMetadata == null || annotationMetadata.isEmpty()) {
            return null;
        }
        return annotationMetadata.enumValue(SerdeConfig.class, member, SerdeBackendMode.class).orElse(null);
    }
}
