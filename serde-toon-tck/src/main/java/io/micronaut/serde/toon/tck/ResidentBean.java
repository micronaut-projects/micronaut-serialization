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
package io.micronaut.serde.toon.tck;

import io.micronaut.serde.annotation.Serdeable;

/**
 * A record with a nested object field, which encodes as a tabular nested field group when listed.
 *
 * @param id      The id
 * @param address The nested address
 * @since 3.3.0
 */
@Serdeable
public record ResidentBean(int id, AddressBean address) {
}
