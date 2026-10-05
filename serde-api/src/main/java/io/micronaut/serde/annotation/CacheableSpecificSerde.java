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

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Lets the registry cache the specific serdes created by a serializer or deserializer bean.
 *
 * <p>Mappers call {@code createSpecific} for every document they read or write. For a bean annotated with
 * {@code @CacheableSpecificSerde}, the registry calls it once per type, annotation metadata and format, and reuses
 * the specific serde for the following documents. The bean can be a singleton whose {@code createSpecific} builds
 * the specific serde, or a bean created with the context and the type as constructor arguments.</p>
 *
 * <p>By annotating a bean, its author guarantees that the specific serde:</p>
 * <ul>
 *     <li>depends only on the serde, serialization and deserialization configuration and the features of the
 *     registry, the type and the format, and not on the context instance it was created with;</li>
 *     <li>keeps no state of the document it serves, and does not retain the context it was created with;</li>
 *     <li>only uses nested serdes that are themselves safe to share between documents. A nested serde created
 *     with the context and the type, which expects every call to pass the context it was created with, is not.</li>
 * </ul>
 *
 * <p>The specific serdes of a context with a view, or of another context implementation, are not cached.</p>
 *
 * @since 3.3.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CacheableSpecificSerde {
}
