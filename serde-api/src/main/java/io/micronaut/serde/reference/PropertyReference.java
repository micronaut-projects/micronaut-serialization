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
package io.micronaut.serde.reference;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.type.Argument;
import org.jspecify.annotations.Nullable;

/**
 * Represents a reference to a property.
 * @param <B> The bean type
 * @param <T> The property type
 */
@Internal
public class PropertyReference<B, T> {
    private final String referenceName;
    @Nullable
    private final BeanIntrospection<B> introspection;
    private final Argument<T> property;
    private final @Nullable Object value;

    public PropertyReference(String referenceName,
                             @Nullable BeanIntrospection<B> introspection,
                             Argument<T> property,
                             @Nullable Object value) {
        this.referenceName = referenceName;
        this.introspection = introspection;
        this.property = property;
        this.value = value;
    }

    /**
     * @return The reference name
     */
    public String getReferenceName() {
        return referenceName;
    }

    /**
     * @return The introspection
     */
    public @Nullable BeanIntrospection<B> getIntrospection() {
        return introspection;
    }

    /**
     * @return The property
     */
    public Argument<T> getProperty() {
        return property;
    }

    /**
     * @return The reference
     */
    public @Nullable Object getReference() {
        return value;
    }
}
