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
package io.micronaut.serde.processor.sourcegen;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.serde.config.annotation.SerdeConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Orders the properties a generated serializer writes the way the runtime object serializer orders them.
 *
 * <p>The runtime serializer writes the properties named by a type-level property order first, in that
 * order, followed by the remaining properties in declaration order, and moves XML attribute properties
 * in front of the element properties without changing their relative order. An alphabetic property
 * order sorts every property by its serialized name.</p>
 *
 * @since 3.2
 */
@Internal
public final class SerdeSourceGenPropertyOrder {

    private SerdeSourceGenPropertyOrder() {
    }

    /**
     * Orders the properties for serialization.
     *
     * @param element        The serialized type
     * @param properties     The properties in declaration order
     * @param serializedName The serialized name of a property
     * @param originalName   The declared name of a property
     * @param xmlAttribute   Whether a property is written as an XML attribute
     * @param <T>            The property type
     * @return The properties in write order
     */
    public static <T> List<T> order(ClassElement element,
                                    List<T> properties,
                                    Function<T, String> serializedName,
                                    Function<T, String> originalName,
                                    Predicate<T> xmlAttribute) {
        String[] explicitOrder = element.stringValues(SerdeConfig.META_ANNOTATION_PROPERTY_ORDER);
        List<T> ordered = new ArrayList<>(properties);
        if (element.booleanValue(SerdeConfig.META_ANNOTATION_PROPERTY_ORDER, "alphabetic").orElse(false)) {
            // The annotation visitor expands an alphabetic order into the sorted serialized names
            ordered.sort(Comparator.comparing(serializedName));
        } else if (explicitOrder.length > 0) {
            Set<String> serializedNames = CollectionUtils.newHashSet(properties.size());
            for (T property : properties) {
                serializedNames.add(serializedName.apply(property));
            }
            ordered.sort(Comparator.comparingInt(property -> {
                int index = explicitIndex(explicitOrder, serializedName.apply(property), originalName.apply(property), serializedNames);
                return index >= 0 ? index : Integer.MAX_VALUE;
            }));
        }
        ordered.sort(Comparator.comparingInt(property -> xmlAttribute.test(property) ? 0 : 1));
        return ordered;
    }

    /**
     * Whether the type declares a property order the generated serializer applies.
     *
     * @param element The serialized type
     * @return {@code true} if a property order is declared on the type
     */
    public static boolean hasExplicitOrder(ClassElement element) {
        return element.stringValues(SerdeConfig.META_ANNOTATION_PROPERTY_ORDER).length > 0
            || element.booleanValue(SerdeConfig.META_ANNOTATION_PROPERTY_ORDER, "alphabetic").orElse(false);
    }

    /**
     * The runtime matches an order entry against the serialized name first, and against the declared
     * name only when no property serializes under that entry.
     */
    private static int explicitIndex(String[] explicitOrder,
                                     String serialized,
                                     String original,
                                     Set<String> serializedNames) {
        for (int i = 0; i < explicitOrder.length; i++) {
            String entry = explicitOrder[i];
            if (serialized.equals(entry) || (original.equals(entry) && !serializedNames.contains(entry))) {
                return i;
            }
        }
        return -1;
    }
}
