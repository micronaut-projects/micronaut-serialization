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

import io.micronaut.core.annotation.Experimental;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A {@link Serdeable} type that opts into compile-time generated serializers and deserializers.
 *
 * @author Denis Stepanov
 * @since 3.0
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Serdeable
@Experimental
public @interface SerdeableGenerated {

    /**
     * @return Whether compilation should fail when a non-skipped serializer or deserializer cannot be generated.
     */
    boolean required() default true;

    /**
     * @return Whether both generated serializer and deserializer should be skipped.
     */
    boolean skip() default false;

    /**
     * @return Whether the generated serializer should be skipped.
     */
    boolean skipSerializer() default false;

    /**
     * @return Whether the generated deserializer should be skipped.
     */
    boolean skipDeserializer() default false;
}
