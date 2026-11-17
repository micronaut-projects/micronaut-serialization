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

import java.util.Objects;

/**
 * Test fixture for a nested JavaBean encoded as an inline CSV value.
 */
@Serdeable
public class CsvBeanPerson {

    private String name;
    private int age;

    /**
     * Creates an empty instance.
     */
    public CsvBeanPerson() {
    }

    /**
     * Creates an instance.
     *
     * @param name The name
     * @param age  The age
     */
    public CsvBeanPerson(String name, int age) {
        this.name = name;
        this.age = age;
    }

    /**
     * Returns the name.
     *
     * @return The name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the name.
     *
     * @param name The name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the age.
     *
     * @return The age
     */
    public int getAge() {
        return age;
    }

    /**
     * Sets the age.
     *
     * @param age The age
     */
    public void setAge(int age) {
        this.age = age;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof CsvBeanPerson that)) {
            return false;
        }
        return age == that.age && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, age);
    }

    @Override
    public String toString() {
        return "CsvBeanPerson[name=" + name + ", age=" + age + "]";
    }
}
