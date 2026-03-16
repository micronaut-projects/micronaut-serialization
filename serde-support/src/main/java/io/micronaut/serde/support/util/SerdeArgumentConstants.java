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
package io.micronaut.serde.support.util;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;

import java.math.BigDecimal;
import java.math.BigInteger;

@Internal
public final class SerdeArgumentConstants {

    public static final Argument<Boolean> BOOLEAN = Argument.of(Boolean.class);
    public static final Argument<Byte> BYTE = Argument.of(Byte.class);
    public static final Argument<Short> SHORT = Argument.of(Short.class);
    public static final Argument<Character> CHARACTER = Argument.of(Character.class);
    public static final Argument<Integer> INTEGER = Argument.of(Integer.class);
    public static final Argument<Long> LONG = Argument.of(Long.class);
    public static final Argument<Float> FLOAT = Argument.of(Float.class);
    public static final Argument<Double> DOUBLE = Argument.of(Double.class);
    public static final Argument<BigInteger> BIG_INTEGER = Argument.of(BigInteger.class);
    public static final Argument<BigDecimal> BIG_DECIMAL = Argument.of(BigDecimal.class);

    private SerdeArgumentConstants() {
    }
}
