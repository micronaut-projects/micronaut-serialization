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
 * Represents a reference path to a specific item within a collection.
 * This interface is a specialized form of {@link ReferencePath}, providing the
 * ability to reference an item within a collection, such as a list or an array.
 *
 * @author Denis Stepanov
 * @since  2.14
 **/
@Experimental
public sealed interface CollectionItemReferencePath extends ReferencePath permits DefaultCollectionItemReferencePath {

    /**
     * @return The collection type (implementation type)
     */
    Class<?> collectionType();

    /**
     * @return The collection argument
     */
    Argument<?> collectionArgument();

    /**
     * @return The index
     */
    int index();

    @Override
    default Argument<?> getArgument() {
        return collectionArgument();
    }
}
