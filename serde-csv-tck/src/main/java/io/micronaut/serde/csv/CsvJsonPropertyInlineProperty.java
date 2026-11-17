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
package io.micronaut.serde.csv;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.csv.annotation.CsvFormat;
import io.micronaut.serde.csv.annotation.CsvProperty;

import java.util.Objects;

/**
 * Test fixture combining {@link JsonProperty} with a property-level {@link CsvFormat}.
 */
@Serdeable
public class CsvJsonPropertyInlineProperty {

    private String message;

    @CsvFormat(delimiter = '|')
    @CsvProperty(name = "inner_data")
    @JsonProperty("inner_data")
    private CsvBeanPerson innerData;

    /**
     * Creates an empty instance.
     */
    public CsvJsonPropertyInlineProperty() {
    }

    /**
     * Creates an instance.
     *
     * @param message   The message
     * @param innerData The nested bean encoded as one pipe-delimited CSV value
     */
    public CsvJsonPropertyInlineProperty(String message, CsvBeanPerson innerData) {
        this.message = message;
        this.innerData = innerData;
    }

    /**
     * Returns the message.
     *
     * @return The message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Sets the message.
     *
     * @param message The message
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Returns the nested bean.
     *
     * @return The nested bean
     */
    public CsvBeanPerson getInnerData() {
        return innerData;
    }

    /**
     * Sets the nested bean.
     *
     * @param innerData The nested bean
     */
    public void setInnerData(CsvBeanPerson innerData) {
        this.innerData = innerData;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CsvJsonPropertyInlineProperty that)) {
            return false;
        }
        return Objects.equals(message, that.message) && Objects.equals(innerData, that.innerData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(message, innerData);
    }
}
