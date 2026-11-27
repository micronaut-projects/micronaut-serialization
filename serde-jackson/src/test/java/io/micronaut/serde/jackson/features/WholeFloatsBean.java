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
package io.micronaut.serde.jackson.features;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/**
 * A bean whose generated serializer reads {@code WRITE_WHOLE_FLOATS_AS_INTEGERS} from its context.
 *
 * @param name        A property that is not a number
 * @param value       A primitive double
 * @param boxed       A boxed double
 * @param single      A primitive float
 * @param boxedSingle A boxed float
 * @param list        A property written by a property serializer
 */
@Serdeable
public record WholeFloatsBean(String name, double value, Double boxed, float single, Float boxedSingle, List<Double> list) {
}
