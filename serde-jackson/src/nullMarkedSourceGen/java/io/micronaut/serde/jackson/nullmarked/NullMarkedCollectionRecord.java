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

import io.micronaut.serde.annotation.Serdeable;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * A null-marked record with non-null collection components.
 *
 * @param tags   The tags
 * @param counts The counts
 * @param items  The nested items
 * @param note   An optional note
 */
@Serdeable
public record NullMarkedCollectionRecord(
    List<String> tags,
    Map<String, Integer> counts,
    List<NullMarkedPlainRecord> items,
    @Nullable String note
) {
}
