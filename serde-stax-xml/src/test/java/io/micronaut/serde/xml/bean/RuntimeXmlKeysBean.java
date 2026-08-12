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

import io.micronaut.serde.annotation.SerdeableGenerated;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.util.List;

/**
 * Runtime-serde counterpart to {@link XmlKeysBean}.
 *
 * @since 3.2
 */
@SerdeableGenerated(skip = true)
public final class RuntimeXmlKeysBean {

    private String name = "";
    private int id;
    private List<String> items = List.of();

    /**
     * Creates an empty bean.
     *
     * @since 3.2
     */
    public RuntimeXmlKeysBean() {
    }

    /**
     * Creates a populated bean.
     *
     * @param name The name
     * @param id The identifier
     * @param items The items
     * @since 3.2
     */
    public RuntimeXmlKeysBean(String name, int id, List<String> items) {
        this.name = name;
        this.id = id;
        this.items = List.copyOf(items);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @JacksonXmlProperty(isAttribute = true)
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @JacksonXmlProperty(localName = "item")
    @JacksonXmlElementWrapper(useWrapping = false)
    public List<String> getItems() {
        return items;
    }

    public void setItems(List<String> items) {
        this.items = List.copyOf(items);
    }
}
