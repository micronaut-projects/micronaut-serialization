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
package io.micronaut.serde.util;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Reusable {@link Argument} constants for generated and runtime serde code.
 */
@Internal
public final class SerdeArgumentConstants {

    /**
     * {@link Argument} constant for {@link Boolean} values.
     */
    public static final Argument<Boolean> BOOLEAN = Argument.of(Boolean.class);
    /**
     * {@link Argument} constant for {@link Byte} values.
     */
    public static final Argument<Byte> BYTE = Argument.of(Byte.class);
    /**
     * {@link Argument} constant for {@link Short} values.
     */
    public static final Argument<Short> SHORT = Argument.of(Short.class);
    /**
     * {@link Argument} constant for {@link Character} values.
     */
    public static final Argument<Character> CHARACTER = Argument.of(Character.class);
    /**
     * {@link Argument} constant for {@link Integer} values.
     */
    public static final Argument<Integer> INTEGER = Argument.of(Integer.class);
    /**
     * {@link Argument} constant for {@link Long} values.
     */
    public static final Argument<Long> LONG = Argument.of(Long.class);
    /**
     * {@link Argument} constant for {@link Float} values.
     */
    public static final Argument<Float> FLOAT = Argument.of(Float.class);
    /**
     * {@link Argument} constant for {@link Double} values.
     */
    public static final Argument<Double> DOUBLE = Argument.of(Double.class);
    /**
     * {@link Argument} constant for {@link BigInteger} values.
     */
    public static final Argument<BigInteger> BIG_INTEGER = Argument.of(BigInteger.class);
    /**
     * {@link Argument} constant for {@link BigDecimal} values.
     */
    public static final Argument<BigDecimal> BIG_DECIMAL = Argument.of(BigDecimal.class);

    private SerdeArgumentConstants() {
    }
}
