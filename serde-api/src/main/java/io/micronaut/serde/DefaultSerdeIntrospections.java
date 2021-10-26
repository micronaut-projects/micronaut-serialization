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
package io.micronaut.serde;

import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;

@Singleton
public class DefaultSerdeIntrospections implements SerdeIntrospections{
    @Override
    public <T> BeanIntrospection<T> getSerializableIntrospection(Argument<T> type) {
        final BeanIntrospection<T> introspection = BeanIntrospector.SHARED.getIntrospection(type.getType());
        if (introspection.hasStereotype(Serdeable.Serializable.class)) {
            return introspection;
        } else {
            throw new IntrospectionException("No serializable introspection present for type. Consider adding Serdeable.Serializable annotate to type " + type);
        }

    }

    @Override
    public <T> BeanIntrospection<T> getDeserializableIntrospection(Argument<T> type) {
        final BeanIntrospection<T> introspection = BeanIntrospector.SHARED.getIntrospection(type.getType());
        if (introspection.hasStereotype(Serdeable.Deserializable.class)) {
            return introspection;
        } else {
            throw new IntrospectionException("No deserializable introspection present for type. Consider adding Serdeable.Deserializable annotate to type " + type);
        }
    }
}
