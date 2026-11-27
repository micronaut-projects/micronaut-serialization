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

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.csv.annotation.CsvFormat;

import java.util.Objects;

/**
 * Test fixture exercising a property-level {@link CsvFormat} annotation on a JavaBean field.
 */
@Serdeable
public class CsvClassInlineProperty {

    private String id;

    @CsvFormat(delimiter = '|')
    private CsvBeanPerson person;

    /**
     * Creates an empty instance.
     */
    public CsvClassInlineProperty() {
    }

    /**
     * Creates an instance.
     *
     * @param id     The outer identifier
     * @param person The nested bean encoded as one pipe-delimited CSV value
     */
    public CsvClassInlineProperty(String id, CsvBeanPerson person) {
        this.id = id;
        this.person = person;
    }

    /**
     * Returns the outer identifier.
     *
     * @return The outer identifier
     */
    public String getId() {
        return id;
    }

    /**
     * Sets the outer identifier.
     *
     * @param id The outer identifier
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Returns the nested bean.
     *
     * @return The nested bean
     */
    public CsvBeanPerson getPerson() {
        return person;
    }

    /**
     * Sets the nested bean.
     *
     * @param person The nested bean
     */
    public void setPerson(CsvBeanPerson person) {
        this.person = person;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CsvClassInlineProperty that)) {
            return false;
        }
        return Objects.equals(id, that.id) && Objects.equals(person, that.person);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, person);
    }

    @Override
    public String toString() {
        return "CsvClassInlineProperty[id=" + id + ", person=" + person + "]";
    }
}
