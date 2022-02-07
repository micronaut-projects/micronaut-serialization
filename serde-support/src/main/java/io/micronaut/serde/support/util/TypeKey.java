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
package io.micronaut.serde.support.util;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;

/**
 * Can be used as a key for type.
 */
public final class TypeKey {
    private final Argument<?> type;
    private final int hashCode;

    public TypeKey(@NonNull Argument<?> type) {
        this.type = type;
        this.hashCode = type.typeHashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TypeKey that = (TypeKey) o;
        return type.equalsType(that.type);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    public @NonNull Argument<?> getType() {
        return type;
    }
}
