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
package io.micronaut.serde.xml;

/**
 * Toggleable read-side features for the XML object mapper.
 *
 * <p>All features default to {@code disabled}. Enable a feature via the
 * {@code micronaut.serde.xml.xml-read-features.<NAME>: true} Micronaut
 * configuration property.</p>
 */
public enum XmlReadFeature {

    /**
     * When enabled, an empty XML element with no content is
     * decoded as {@code null} instead of an empty string / empty bean.
     *
     * <p>Default ({@code disabled}) matches Jackson 3.x default behaviour:
     * empty elements decode to {@code ""} for scalar fields and to an empty
     * bean (constructor invoked with defaults) for object-typed fields.</p>
     */
    EMPTY_ELEMENT_AS_NULL
}
