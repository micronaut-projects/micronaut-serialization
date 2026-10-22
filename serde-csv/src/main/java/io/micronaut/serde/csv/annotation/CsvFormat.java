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
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the CSV format used to read and write a bean type or a bean property.
 *
 * <p>On a type, the annotation configures the top-level CSV record. On a property, getter,
 * setter, or record component, the annotated value is written as a single inline CSV value
 * using this format, and is parsed back into the property type when reading. This is useful
 * for nested beans that should occupy one column rather than separate top-level columns.</p>
 *
 * <p>The annotation is read at runtime from the compile-time generated
 * {@link io.micronaut.core.beans.BeanIntrospection} metadata, so it works for both the
 * Apache Commons CSV and the Jackson CSV backends and is native-image friendly. When the
 * annotation is absent the backend defaults are used.</p>
 *
 * <pre>
 * &#64;Serdeable
 * &#64;CsvFormat(delimiter = ';', header = CsvFormat.Header.PRESENT)
 * record Person(String name, int age) {}
 *
 * &#64;Serdeable
 * record Employee(String id, &#64;CsvFormat(delimiter = '|') Person person) {}
 * // Writes the person property as one column, for example "Alice|30"
 * </pre>
 *
 * @see CsvProperty
 * @since 3.2.5
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({
    ElementType.TYPE,
    ElementType.FIELD,
    ElementType.METHOD,
    ElementType.PARAMETER,
    ElementType.RECORD_COMPONENT
})
@Inherited
public @interface CsvFormat {

    /**
     * Controls whether the first CSV line is a header row.
     *
     * @return The header mode
     */
    Header header() default Header.INFER;

    /**
     * Explicit column names in the order they appear in the CSV. When set, header handling
     * is derived from {@link #header()} and the property declaration order is ignored.
     *
     * @return The explicit column names
     */
    String[] columns() default {};

    /**
     * The delimiter separating columns. Defaults to a comma.
     *
     * @return The delimiter
     */
    char delimiter() default ',';

    /**
     * The quote character used to escape delimiters. Defaults to a double quote.
     *
     * @return The quote character
     */
    char quote() default '"';

    /**
     * The escape character. Defaults to a double quote, which enables the standard
     * doubled-quote escaping.
     *
     * @return The escape character
     */
    char escape() default '"';

    /**
     * The line separator used when writing. Defaults to a line feed.
     *
     * @return The line separator
     */
    String lineSeparator() default "\n";

    /**
     * The value that represents a {@code null} column. Defaults to the empty string.
     *
     * @return The null value representation
     */
    String nullValue() default "";

    /**
     * Whether empty lines are skipped while reading. Defaults to {@code true}.
     *
     * @return Whether empty lines are skipped
     */
    boolean skipEmptyLines() default true;

    /**
     * Whether surrounding whitespace is trimmed from column values. Defaults to {@code false}.
     *
     * @return Whether values are trimmed
     */
    boolean trim() default false;

    /**
     * Constrains how many CSV records map to the target value. Defaults to inferring the
     * constraint from the target type.
     *
     * @return The record shape
     */
    Shape shape() default Shape.INFER;

    /**
     * Header handling.
     *
     * @since 3.2.5
     */
    enum Header {
        /**
         * Infer the behavior from the backend defaults: a header is expected when reading
         * and is not emitted when writing.
         */
        INFER,
        /**
         * A header row is expected when reading and is emitted when writing.
         */
        PRESENT,
        /**
         * There is no header row; columns follow the declared order.
         */
        ABSENT
    }

    /**
     * How CSV records map to the target value.
     *
     * @since 3.2.5
     */
    enum Shape {
        /**
         * Infer from the target type: {@link Iterable} targets read and write every record,
         * any other type maps a single record.
         */
        INFER,
        /**
         * Exactly one CSV record maps to the value. Additional records are rejected.
         */
        SINGLE,
        /**
         * Only the first CSV record maps to the value; additional records are ignored.
         */
        FIRST,
        /**
         * Every CSV record maps to an element of a collection value.
         */
        MULTI
    }
}
