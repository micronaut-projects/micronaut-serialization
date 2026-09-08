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
 * CSV point fixture.
 */
@Serdeable
public final class CsvPoint {

    private String x;
    private String y;
    private String visible;

    /**
     * Creates an empty CSV point.
     */
    public CsvPoint() {
    }

    /**
     * Creates a CSV point.
     *
     * @param x The x coordinate
     * @param y The y coordinate
     * @param visible Whether the point is visible
     */
    public CsvPoint(String x, String y, String visible) {
        this.x = x;
        this.y = y;
        this.visible = visible;
    }

    /**
     * @return The x coordinate
     */
    public String getX() {
        return x;
    }

    /**
     * @param x The x coordinate
     */
    public void setX(String x) {
        this.x = x;
    }

    /**
     * @return The y coordinate
     */
    public String getY() {
        return y;
    }

    /**
     * @param y The y coordinate
     */
    public void setY(String y) {
        this.y = y;
    }

    /**
     * @return Whether the point is visible
     */
    public String getVisible() {
        return visible;
    }

    /**
     * @param visible Whether the point is visible
     */
    public void setVisible(String visible) {
        this.visible = visible;
    }
}
