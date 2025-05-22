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
 * Represents a reference path used for tracking references within serialized or deserialized data structures.
 * This interface serves as a base for specific types of reference paths such as property references,
 * collection item references, and map item references.
 * <p>
 * It allows encapsulating and constructing paths for identifying specific items or properties
 * in complex object graphs, making it useful in serialization and deserialization processes where
 * tracking the location of errors or data modifications is required.
 *
 * @author Denis Stepanov
 * @since 2.14
 */
@Experimental
public sealed interface ReferencePath permits CollectionItemReferencePath, MapItemReferencePath, PropertyReferencePath {

    /**
     * Creates a reference path for a property of a specified bean type.
     *
     * @param beanType The class of the bean containing the property.
     * @param property The argument representing the property.
     * @return A {@link ReferencePath} instance representing the property reference path.
     */
    static ReferencePath ofProperty(Class<?> beanType, Argument<?> property) {
        return new DefaultPropertyReferencePath(beanType, property);
    }

    /**
     * Creates a reference path for a specific item within a collection.
     *
     * @param type               The class type of the collection.
     * @param collectionArgument The collection argument.
     * @param index              The index of the item within the collection.
     * @return A {@link ReferencePath} instance representing the collection item reference path.
     */
    static ReferencePath ofCollection(Class<?> type, Argument<?> collectionArgument, int index) {
        return new DefaultCollectionItemReferencePath(type, collectionArgument, index);
    }

    /**
     * Creates a reference path for a specific item within a map.
     *
     * @param type        The class type of the map.
     * @param mapArgument The map argument.
     * @param key         The key of the item within the map.
     * @return A {@link ReferencePath} instance representing the map item reference path.
     */
    static ReferencePath ofMap(Class<?> type, Argument<?> mapArgument, String key) {
        return new DefaultMapItemReferencePath(type, mapArgument, key);
    }

    /**
     * @return The referenced argument
     */
    Argument<?> getArgument();

}
