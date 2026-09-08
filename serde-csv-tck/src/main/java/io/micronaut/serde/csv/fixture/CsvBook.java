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

/**
 * CSV book fixture.
 */
@Serdeable
public final class CsvBook {
    private String title;
    private String pages;
    private String available;

    /**
     * @return The title
     */
    public String getTitle() {
        return title;
    }

    /**
     * @param title The title
     */
    public void setTitle(String title) {
        this.title = title;
    }

    /**
     * @return The number of pages
     */
    public String getPages() {
        return pages;
    }

    /**
     * @param pages The number of pages
     */
    public void setPages(String pages) {
        this.pages = pages;
    }

    /**
     * @return Whether the book is available
     */
    public String getAvailable() {
        return available;
    }

    /**
     * @param available Whether the book is available
     */
    public void setAvailable(String available) {
        this.available = available;
    }
}
