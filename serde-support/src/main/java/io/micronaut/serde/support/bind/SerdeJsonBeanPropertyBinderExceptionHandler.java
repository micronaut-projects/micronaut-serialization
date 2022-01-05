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
package io.micronaut.serde.support.bind;

import java.util.Optional;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.exceptions.ConversionErrorException;
import io.micronaut.core.type.Argument;
import io.micronaut.json.bind.JsonBeanPropertyBinderExceptionHandler;
import io.micronaut.serde.exceptions.InvalidFormatException;
import io.micronaut.serde.exceptions.InvalidPropertyFormatException;
import jakarta.inject.Singleton;

/**
 * Implementation of {@link JsonBeanPropertyBinderExceptionHandler}.
 */
@Singleton
@Internal
public class SerdeJsonBeanPropertyBinderExceptionHandler
        implements JsonBeanPropertyBinderExceptionHandler {
    @Override
    public Optional<ConversionErrorException> toConversionError(Object object, Exception e) {
        if (e instanceof InvalidFormatException) {
            InvalidFormatException ife = (InvalidFormatException) e;
            Argument<?> argument;
            if (ife instanceof InvalidPropertyFormatException) {
                argument = ((InvalidPropertyFormatException) ife).getArgument();
            } else {
                Class<?> type = object != null ? object.getClass() : Object.class;
                argument = Argument.of(type);
            }
            return Optional.of(new ConversionErrorException(argument, ife.toConversionError()));
        }
        return Optional.empty();
    }
}
