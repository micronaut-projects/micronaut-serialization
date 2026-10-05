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
package io.micronaut.serde.annotation;

import io.micronaut.core.annotation.Internal;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a serializer or deserializer bean created with the encoder or decoder context and the type as constructor
 * arguments whose specific serde depends only on the registry configuration, the type and the format, and not on
 * the context instance it was created with. The registry creates such a bean once per type and format instead of once
 * per document.
 *
 * <p>The build-time generated serdes are annotated with it.</p>
 *
 * @since 3.2.5
 */
@Internal
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CacheableSpecificSerde {
}
