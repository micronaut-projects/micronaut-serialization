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
package io.micronaut.serde.csv;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.csv.annotation.CsvFormat;

/**
 * Test fixture exercising a property-level {@link CsvFormat} annotation.
 *
 * @param id     The outer identifier
 * @param person The nested bean encoded as one pipe-delimited CSV value
 */
@Serdeable
public record CsvInlineProperty(String id, @CsvFormat(delimiter = '|') Person person) {
}
