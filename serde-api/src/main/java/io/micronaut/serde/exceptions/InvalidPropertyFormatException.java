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
package io.micronaut.serde.exceptions;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;

/**
 * Extended version of {@link io.micronaut.serde.exceptions.InvalidFormatException} with information
 * about the property that was incorrect.
 *
 * @since 1.0.0
 */
public class InvalidPropertyFormatException extends InvalidFormatException {
    private final Argument<?> argument;

    /**
     * Construct a more detailed exception from the given cause.
     * @param cause The cause
     * @param argument The argument
     */
    public InvalidPropertyFormatException(@NonNull InvalidFormatException cause, @NonNull Argument<?> argument) {
        super("Cannot deserialize " + argument + " due to: " + cause.getMessage(), cause, cause.getOriginalValue());
        this.argument = argument;
    }

    /**
     * @return The property.
     */
    public @NonNull Argument<?> getArgument() {
        return argument;
    }
}
