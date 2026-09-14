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
package io.micronaut.serde.jackson.nullmarked;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

/**
 * A null-marked record with renamed non-null components, an enum and a nested record.
 *
 * @param displayName The display name
 * @param status      The status
 * @param plain       The nested record
 */
@Serdeable
public record NullMarkedRenamedRecord(
    @JsonProperty("display_name") String displayName,
    @JsonProperty("state") NullMarkedStatus status,
    @JsonProperty("nested") NullMarkedPlainRecord plain
) {
}
