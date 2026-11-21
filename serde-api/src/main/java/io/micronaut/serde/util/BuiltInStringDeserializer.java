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

/**
 * Marks the string deserializer Micronaut Serialization registers itself, which returns the string of the input
 * unchanged. A generated enum deserializer matches the constant names in the input directly only when the string
 * deserializer it resolved is this one, so that any other string deserializer still sees every value.
 *
 * @since 3.3.0
 */
@Internal
public interface BuiltInStringDeserializer {
}
