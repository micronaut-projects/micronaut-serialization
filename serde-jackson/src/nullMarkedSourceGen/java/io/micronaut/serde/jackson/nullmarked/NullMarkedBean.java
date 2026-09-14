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

import java.util.ArrayList;
import java.util.List;

/**
 * A null-marked default-constructor bean with non-null properties.
 */
@Serdeable
public class NullMarkedBean {

    private String name = "";
    private List<String> tags = new ArrayList<>();
    private NullMarkedPlainRecord plain = new NullMarkedPlainRecord("", 0);

    /**
     * @return The name
     */
    @JsonProperty("bean_name")
    public String getName() {
        return name;
    }

    /**
     * @param name The name
     */
    @JsonProperty("bean_name")
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return The tags
     */
    public List<String> getTags() {
        return tags;
    }

    /**
     * @param tags The tags
     */
    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    /**
     * @return The nested record
     */
    public NullMarkedPlainRecord getPlain() {
        return plain;
    }

    /**
     * @param plain The nested record
     */
    public void setPlain(NullMarkedPlainRecord plain) {
        this.plain = plain;
    }
}
