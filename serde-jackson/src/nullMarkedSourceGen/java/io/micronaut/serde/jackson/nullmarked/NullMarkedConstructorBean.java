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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

/**
 * A null-marked bean bound through its constructor with non-null properties.
 */
@Serdeable
public final class NullMarkedConstructorBean {

    private final String name;
    private final List<String> tags;

    /**
     * @param name The name
     * @param tags The tags
     */
    @JsonCreator
    public NullMarkedConstructorBean(@JsonProperty("name") String name, @JsonProperty("tags") List<String> tags) {
        this.name = name;
        this.tags = tags;
    }

    /**
     * @return The name
     */
    public String getName() {
        return name;
    }

    /**
     * @return The tags
     */
    public List<String> getTags() {
        return tags;
    }
}
