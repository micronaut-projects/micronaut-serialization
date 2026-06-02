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
package io.micronaut.serde.jsonb;

import io.micronaut.core.annotation.Internal;
import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Internal metadata used by the JSON-B annotation transformers.
 */
@Internal
@Retention(RetentionPolicy.RUNTIME)
@interface JsonbSerdeConfig {
    Class<? extends JsonbAdapter> adapter() default JsonbAdapter.class;

    Class<? extends JsonbSerializer> serializer() default JsonbSerializer.class;

    Class<? extends JsonbDeserializer> deserializer() default JsonbDeserializer.class;

    String customization() default "";
}
