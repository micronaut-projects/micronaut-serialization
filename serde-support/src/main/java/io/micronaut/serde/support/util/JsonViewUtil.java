/*
 * Copyright 2017-2024 original authors
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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.annotation.SerdeConfig;
import org.jetbrains.annotations.Contract;

import java.util.Optional;

/**
 * Utilities for implementing JsonView support in mappers.
 *
 * @author Jonas Konrad
 * @since 2.12
 */
@Internal
public final class JsonViewUtil {
    private JsonViewUtil() {
    }

    /**
     * Extract a view annotation from an argument, if present.
     *
     * @param configuration Configuration for checking {@link SerdeConfiguration#isJsonViewEnabled()}
     * @param argument      The argument to check
     * @param defaultValue  The fallback view
     * @return The extracted view, or {@code defaultValue} as a fallback
     */
    @Contract(pure = true, value = "_, _, !null -> !null")
    public static Class<?> extractView(@Nullable SerdeConfiguration configuration, @NonNull Argument<?> argument, Class<?> defaultValue) {
        if (configuration != null && configuration.isJsonViewEnabled()) {
            AnnotationMetadata annotationMetadata = argument.getAnnotationMetadata();
            Optional<Class> jackson = annotationMetadata.classValue("com.fasterxml.jackson.annotation.JsonView");
            if (jackson.isPresent()) {
                return jackson.get();
            }
            Optional<Class> serde = annotationMetadata.classValue(SerdeConfig.class, SerdeConfig.VIEWS);
            if (serde.isPresent()) {
                return serde.get();
            }
        }
        return defaultValue;
    }
}
