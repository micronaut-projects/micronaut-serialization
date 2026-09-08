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
package io.micronaut.serde.yaml.tck;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * A bean holding every numeric type, used to exercise YAML core schema numbers.
 *
 * @param i The int value
 * @param l The long value
 * @param d The double value
 * @param f The float value
 * @param bigInteger The big integer value
 * @param bigDecimal The big decimal value
 */
@Serdeable
public record NumbersBean(@Nullable Integer i,
                          @Nullable Long l,
                          @Nullable Double d,
                          @Nullable Float f,
                          @Nullable BigInteger bigInteger,
                          @Nullable BigDecimal bigDecimal) {
}
