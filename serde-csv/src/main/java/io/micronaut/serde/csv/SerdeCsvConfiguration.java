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

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.config.SerdeConfiguration;

import java.util.Objects;

/**
 * CSV-specific configuration.
 */
@Internal
@ConfigurationProperties(SerdeCsvConfiguration.PREFIX)
public final class SerdeCsvConfiguration {
    static final String PREFIX = SerdeConfiguration.PREFIX + ".csv";

    private ReadFeatures readFeatures = new ReadFeatures();
    private WriteFeatures writeFeatures = new WriteFeatures();

    /**
     * Returns the CSV read features.
     *
     * @return The CSV read features
     */
    public ReadFeatures getReadFeatures() {
        return readFeatures;
    }

    /**
     * Sets the CSV read features.
     *
     * @param readFeatures The CSV read features
     */
    public void setReadFeatures(ReadFeatures readFeatures) {
        this.readFeatures = Objects.requireNonNull(readFeatures, "readFeatures");
    }

    /**
     * Returns the CSV write features.
     *
     * @return The CSV write features
     */
    public WriteFeatures getWriteFeatures() {
        return writeFeatures;
    }

    /**
     * Sets the CSV write features.
     *
     * @param writeFeatures The CSV write features
     */
    public void setWriteFeatures(WriteFeatures writeFeatures) {
        this.writeFeatures = Objects.requireNonNull(writeFeatures, "writeFeatures");
    }

    /**
     * Returns the configured header handling.
     *
     * @return The configured header handling
     */
    public Header getHeader() {
        return readFeatures.getHeader();
    }

    /**
     * Returns the configured write header handling.
     *
     * @return The configured write header handling
     */
    public Header getWriteHeader() {
        return writeFeatures.getHeader();
    }

    /**
     * CSV header handling for reading and writing.
     */
    public enum Header {
        /**
         * No header row is present. Object row keys are generated from zero-based column indexes.
         */
        NONE,

        /**
         * When reading, the first CSV row contains column names used as object row keys.
         * When writing, column names are emitted as the first CSV row.
         */
        FIRST_ROW
    }

    /**
     * Controls CSV deserialization behavior.
     */
    @ConfigurationProperties("read-features")
    public static final class ReadFeatures {
        private Header header = Header.NONE;

        /**
         * Returns the configured header handling.
         *
         * @return The configured header handling
         */
        public Header getHeader() {
            return header;
        }

        /**
         * Sets the header handling.
         *
         * @param header The header handling
         */
        public void setHeader(Header header) {
            this.header = Objects.requireNonNull(header, "header");
        }
    }

    /**
     * Controls CSV serialization behavior.
     */
    @ConfigurationProperties("write-features")
    public static final class WriteFeatures {
        private Header header = Header.NONE;

        /**
         * Returns the configured header handling.
         *
         * @return The header handling
         */
        public Header getHeader() {
            return header;
        }

        /**
         * Sets the header handling.
         *
         * @param header The header handling
         */
        public void setHeader(Header header) {
            this.header = Objects.requireNonNull(header, "header");
        }
    }
}
