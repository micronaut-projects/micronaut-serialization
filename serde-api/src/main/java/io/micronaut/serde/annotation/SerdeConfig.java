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
package io.micronaut.serde.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.micronaut.context.annotation.Executable;
import io.micronaut.core.annotation.Internal;

/**
 * Meta-annotation with meta annotation members that different annotation
 * models can be bind to.
 *
 * <p>This annotation shouldn't be used directly instead a concrete annotation
 * API for JSON like JSON-B or Jackson annotations should be used.</p>
 */
@Internal
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.ANNOTATION_TYPE)
@Inherited
public @interface SerdeConfig {
    /**
     * The property to use.
     */
    String PROPERTY = "property";

    /**
     * Is it ignored.
     */
    String IGNORED = "ignored";

    /**
     * Include strategy.
     */
    String INCLUDE = "include";

    /**
     * Is this property to be used only for reading.
     */
    String READ_ONLY = "readOnly";

    /**
     * Is this property to be used only for writing.
     */
    String WRITE_ONLY = "writeOnly";

    /**
     * A type name mapping. Used for subtype binding.
     */
    String TYPE_NAME = "typeName";

    /**
     * The type property used for subtype binding.
     */
    String TYPE_PROPERTY = "typeProperty";

    /**
     * A property that should be used to wrap this value when serializing.
     */
    String WRAPPER_PROPERTY = "wrapperProperty";

    /**
     * Internal metadata type for wrapped settings.
     */
    @Internal
    @interface Unwrapped {
        String NAME = Unwrapped.class.getName();
        String PREFIX = "prefix";
        String SUFFIX = "suffix";
    }

    /**
     * Internal metadata for a JSON getter.
     */
    @Internal
    @Executable
    @interface Getter {
    }

    /**
     * Internal metadata for a JSON any getter.
     */
    @Internal
    @Executable
    @interface AnyGetter {
    }

    /**
     * Internal metadata for a setter.
     */
    @Internal
    @Executable
    @interface Setter {
    }

    /**
     * Internal metadata for any setter.
     */
    @Internal
    @Executable
    @interface AnySetter {
    }

    /**
     * Used to store errors.
     */
    @Internal
    @interface Error {
    }

    /**
     * Meta annotations for subtyped mapping.
     */
    @Internal
    @interface Subtyped {
        /**
         * The discriminator to use.
         */
        String DISCRIMINATOR_TYPE = "dt";

        /**
         * The discriminator value to use.
         */
        String DISCRIMINATOR_VALUE = "dv";

        /**
         * The discriminator property to use.
         */
        String DISCRIMINATOR_PROP = "dp";

        enum DiscriminatorType {
            PROPERTY, WRAPPER_OBJECT
        }

        enum DiscriminatorValueKind {
            CLASS, NAME
        }
    }

    /**
     * Include strategies.
     */
    @Internal
    enum Include {

        /**
         * Value that indicates that property is to be always included,
         * independent of value of the property.
         */
        ALWAYS,

        /**
         * Value that indicates that only properties with non-null
         * values are to be included.
         */
        NON_NULL,

        /**
         * Value that indicates that properties are included unless their value
         * is:
         *<ul>
         *  <li>null</li>
         *  <li>"absent" value of a referential type (like Java 8 `Optional`, or
         *     {link java.util.concurrent.atomic.AtomicReference}); that is, something
         *     that would not deference to a non-null value.
         * </ul>
         */
        NON_ABSENT,

        /**
         * Value that indicates that only properties with null value,
         * or what is considered empty, are not to be included.
         */
        NON_EMPTY
    }
}
