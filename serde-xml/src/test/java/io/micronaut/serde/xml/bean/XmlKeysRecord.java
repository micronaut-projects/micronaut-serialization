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
package io.micronaut.serde.xml.bean;

import io.micronaut.serde.annotation.Serdeable;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.util.List;

/**
 * Source-generated record used to verify XML key metadata and constructor ordering.
 *
 * @param name The name
 * @param id The identifier
 * @param items The items
 * @since 3.2
 */
@Serdeable
public record XmlKeysRecord(
    String name,
    @JacksonXmlProperty(isAttribute = true) int id,
    @JacksonXmlProperty(localName = "item")
    @JacksonXmlElementWrapper(useWrapping = false) List<String> items
) {
    /**
     * Copies mutable component values.
     *
     * @since 3.2
     */
    public XmlKeysRecord {
        items = List.copyOf(items);
    }
}
