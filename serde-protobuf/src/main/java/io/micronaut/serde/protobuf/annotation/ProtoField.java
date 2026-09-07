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
package io.micronaut.serde.protobuf.annotation;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.core.annotation.Experimental;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Assigns a Protocol Buffers field number, and optionally a wire representation, to a property.
 *
 * <p>Field numbers are the identity of a property on the wire; names are never transmitted. A
 * number can be given explicitly, with {@code @ProtoField(3)} or {@code @ProtoField(position = 3)},
 * or left out, in which case the property takes its number from its position among the message's
 * properties: the first is 1, the second is 2, and so on. A property with no annotation at all is
 * numbered the same way.</p>
 *
 * <p>Positions derived from order are convenient, and they are also fragile: inserting, removing or
 * reordering a property silently renumbers everything after it, and payloads written by an earlier
 * version are then read back into the wrong properties. Derived numbering suits a message whose
 * writer and readers are deployed together. Anything that outlives a single deployment &mdash;
 * stored payloads, a published API, messages on a queue &mdash; should number its fields
 * explicitly.</p>
 *
 * <p>This is prototype API and subject to change.</p>
 *
 * @since 3.2
 */
@Experimental
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT, ElementType.ANNOTATION_TYPE})
public @interface ProtoField {

    /**
     * The lowest legal Protocol Buffers field number.
     */
    int MIN_FIELD_NUMBER = 1;

    /**
     * The highest legal Protocol Buffers field number.
     */
    int MAX_FIELD_NUMBER = 536870911;

    /**
     * The value of {@link #position()} that means "take the number from the property's position
     * among the message's properties".
     */
    int UNSET_POSITION = -1;

    /**
     * The field number. An alias for {@link #position()}, so {@code @ProtoField(3)} and
     * {@code @ProtoField(position = 3)} mean the same thing.
     *
     * @return The field number, or {@link #UNSET_POSITION} to derive it from declaration order
     */
    @AliasFor(member = "position")
    int value() default UNSET_POSITION;

    /**
     * The field number. Must be between {@link #MIN_FIELD_NUMBER} and {@link #MAX_FIELD_NUMBER},
     * and must not fall in the range 19000-19999 that Protocol Buffers reserves.
     *
     * <p>Left at {@link #UNSET_POSITION}, the property is numbered by its position among the
     * message's properties, counting from one.</p>
     *
     * @return The field number, or {@link #UNSET_POSITION} to derive it from declaration order
     */
    @AliasFor(member = "value")
    int position() default UNSET_POSITION;

    /**
     * The wire representation to use. Defaults to {@link ProtoType#DEFAULT}, which picks the
     * representation from the Java type.
     *
     * @return The proto type
     */
    ProtoType type() default ProtoType.DEFAULT;
}
