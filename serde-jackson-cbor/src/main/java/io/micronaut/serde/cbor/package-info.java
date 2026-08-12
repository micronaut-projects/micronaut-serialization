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
/**
 * CBOR object mapper support for Micronaut Serialization.
 *
 * <p>Uses Jackson's streaming CBOR factory ({@code jackson-dataformat-cbor}) only as a
 * token-level parser/generator. Object graph mapping is performed by Micronaut Serde
 * (build-time serializers/deserializers), not Jackson Databind.</p>
 */
@NullMarked
package io.micronaut.serde.cbor;

import org.jspecify.annotations.NullMarked;
