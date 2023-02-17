/*
 * Copyright 2017-2023 original authors
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

import io.micronaut.core.annotation.Indexed;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;

/**
 * Models a build time property filter. That is a class computed at build-time that can
 * be used to decide which bean properties to serialize.
 * Use {@link jakarta.inject.Named} annotation to specify a name for the property filter.
 *
 * @author Andriy Dmytruk
 */
@Indexed(PropertyFilter.class)
public interface PropertyFilter {

    /**
     *
     * @param encoderContext the encoder context
     * @param propertySerializer the serializer of the property
     * @param bean the object being serialized
     * @param propertyName the name of the property
     * @param propertyValue the property being serialized
     *
     * @return whether the property should be included in serialization
     */
    boolean shouldInclude(@NonNull Serializer.EncoderContext encoderContext, @NonNull Serializer<Object> propertySerializer, @NonNull Object bean, @NonNull String propertyName, @Nullable Object propertyValue);
}
