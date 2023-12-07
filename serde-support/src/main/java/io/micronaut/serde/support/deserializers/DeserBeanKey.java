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
package io.micronaut.serde.support.deserializers;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.support.util.SerdeArgumentConf;

import java.util.Objects;

/**
 * The {@link DeserBean} caching key.
 */
@Internal
final class DeserBeanKey {
    private final Argument<?> type;
    @Nullable
    private final SerdeArgumentConf serdeArgumentConf;
    private final int hashCode;

    public DeserBeanKey(@NonNull Argument<?> type, @Nullable SerdeArgumentConf serdeArgumentConf) {
        this.type = type;
        this.serdeArgumentConf = serdeArgumentConf;
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
        DeserBeanKey that = (DeserBeanKey) o;
        return type.equalsType(that.type) && Objects.equals(serdeArgumentConf, that.serdeArgumentConf);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    public @NonNull Argument<?> getType() {
        return type;
    }
}
