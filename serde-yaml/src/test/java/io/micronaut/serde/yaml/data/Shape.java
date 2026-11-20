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
package io.micronaut.serde.yaml.data;

import io.micronaut.serde.annotation.Serdeable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A model whose map values are declared as the base type and may hold subtypes (#1505).
 */
@Serdeable
public class Shape {
    private String type;
    private Map<String, Shape> properties;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, Shape> getProperties() {
        return properties;
    }

    public void setProperties(Map<String, Shape> properties) {
        this.properties = properties;
    }

    public Shape addProperty(String name, Shape shape) {
        if (properties == null) {
            properties = new LinkedHashMap<>();
        }
        properties.put(name, shape);
        return this;
    }
}
