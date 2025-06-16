/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.serde.exceptions.path;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.type.Argument;

/**
 * Represents a reference path to a specific property of a bean type.
 * This interface is a specialized form of {@link ReferencePath}, providing
 * the ability to reference a property within a complex object graph.
 * <p>
 * It is used in serialization and deserialization processes to trace
 * the specific property where an error or modification occurs. This can
 * assist in debugging or error reporting by identifying the exact location
 * within an object structure.
 *
 * @author Denis Stepanov
 * @since 2.14
 */
@Experimental
public sealed interface PropertyReferencePath extends ReferencePath permits DefaultPropertyReferencePath {

    /**
     * @return The type
     */
    Class<?> type();

    /**
     * @return The property
     */
    Argument<?> property();

    @Override
    default Argument<?> getArgument() {
        return property();
    }
}
