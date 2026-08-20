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
package io.micronaut.serde.csv.fixture;

import io.micronaut.serde.annotation.Serdeable;

import java.util.Map;

/**
 * CSV fixture with a map property.
 */
@Serdeable
public final class CsvBeanWithMap {
    private String name;
    private Map<String, String> values;

    /**
     * Creates an empty CSV bean.
     */
    public CsvBeanWithMap() {
    }

    /**
     * Creates a CSV bean.
     *
     * @param name The name
     * @param values The values
     */
    public CsvBeanWithMap(String name, Map<String, String> values) {
        this.name = name;
        this.values = values;
    }

    /**
     * @return The name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name The name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return The values
     */
    public Map<String, String> getValues() {
        return values;
    }

    /**
     * @param values The values
     */
    public void setValues(Map<String, String> values) {
        this.values = values;
    }
}
