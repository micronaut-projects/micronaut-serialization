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
package io.micronaut.serde.csv.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Customizes how a bean property maps to a CSV column.
 *
 * <p>When present, the annotation drives the CSV column name and order used by the
 * BeanIntrospection based runtime helpers. It is read from the property metadata generated
 * at compile time and does not rely on reflection.</p>
 *
 * @see CsvFormat
 * @since 3.2.5
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
public @interface CsvProperty {

    /**
     * The CSV column name. When empty the property name is used.
     *
     * @return The column name
     */
    String name() default "";

    /**
     * The explicit column position. A negative value keeps the declaration order. Properties
     * with an explicit order are written before the remaining properties, ordered by this value.
     *
     * @return The column position
     */
    int order() default -1;

    /**
     * Whether the property is excluded from CSV mapping.
     *
     * @return {@code true} to ignore the property
     */
    boolean ignore() default false;
}
