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

/**
 * A bean holding the scalar shapes YAML distinguishes, used to exercise scalar typing.
 *
 * @param text The string value
 * @param flag The boolean value
 * @param number The integer value
 * @param decimal The floating point value
 * @param nothing The null value
 */
@Serdeable
public record ScalarsBean(@Nullable String text,
                          @Nullable Boolean flag,
                          @Nullable Integer number,
                          @Nullable Double decimal,
                          @Nullable String nothing) {
}
