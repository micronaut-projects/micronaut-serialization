/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.serde.config;

/**
 * The baseline for the scalar coercions a decoder performs. Individual coercions can always be
 * turned on or off explicitly, the mode only decides the default for the ones that are not
 * configured.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
public enum CoercionMode {
    /**
     * Values of the wrong shape are coerced where a sensible conversion exists, for example a JSON
     * string is read into an {@code int} property. This is the historical behaviour.
     */
    LENIENT,
    /**
     * Values must have the shape of the target type. A JSON string is not read into an {@code int}
     * property, a floating point number is not read into an integer property, and so on.
     */
    STRICT
}
