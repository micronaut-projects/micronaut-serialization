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
package io.micronaut.serde.toml.support;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.bind.annotation.Bindable;
import io.micronaut.core.util.StringUtils;
import io.micronaut.serde.config.SerdeConfiguration;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

/**
 * TOML-specific configuration.
 */
@BootstrapContextCompatible
@Internal
@ConfigurationProperties(SerdeTomlConfiguration.PREFIX)
public final class SerdeTomlConfiguration {
    static final String PREFIX = SerdeConfiguration.PREFIX + ".toml";

    private ReadConstraints readConstraints = new ReadConstraints();
    private WriteFeatures writeFeatures = new WriteFeatures();

    public ReadConstraints getReadConstraints() {
        return readConstraints;
    }

    public void setReadConstraints(ReadConstraints readConstraints) {
        this.readConstraints = readConstraints;
    }

    public WriteFeatures getWriteFeatures() {
        return writeFeatures;
    }

    public void setWriteFeatures(WriteFeatures writeFeatures) {
        this.writeFeatures = writeFeatures;
    }

    public WriteLayout getWriteLayout() {
        return writeFeatures.getWriteLayout();
    }

    public @Nullable Integer getMaxNumberLength() {
        return readConstraints.getMaxNumberLength();
    }

    public @Nullable Integer getMaxStringLength() {
        return readConstraints.getMaxStringLength();
    }

    public boolean isFailOnNullWrite() {
        return writeFeatures.isFailOnNullWrite();
    }

    /**
     * TOML writer layout.
     */
    public enum WriteLayout {
        TABLE,
        INLINE
    }

    /**
     * TOML read constraints.
     */
    @ConfigurationProperties("read-constraints")
    public static final class ReadConstraints {
        @Nullable
        private Integer maxNumberLength;
        @Nullable
        private Integer maxStringLength;

        public @Nullable Integer getMaxNumberLength() {
            return maxNumberLength;
        }

        public void setMaxNumberLength(@Nullable Integer maxNumberLength) {
            this.maxNumberLength = maxNumberLength;
        }

        public @Nullable Integer getMaxStringLength() {
            return maxStringLength;
        }

        public void setMaxStringLength(@Nullable Integer maxStringLength) {
            this.maxStringLength = maxStringLength;
        }
    }

    /**
     * Controls TOML serialization behavior.
     *
     * <p>The <a href="https://toml.io/en/v1.0.0#string">TOML v1.0.0 specification</a> defines no
     * null type — values must be one of: String, Integer, Float, Boolean, Date-Time, Array, or
     * Inline Table. By default, null Java fields are written as empty strings ({@code ''}) to
     * produce valid TOML, but this can mask the distinction between absent and empty values.</p>
     *
     * <p>Example configuration in {@code application.yml}:</p>
     * <pre>{@code
     * micronaut:
     *   serde:
     *     toml:
     *       write-features:
     *         fail-on-null-write: true
     * }</pre>
     *
     * <p>With {@code fail-on-null-write: false} (default):</p>
     * <pre>{@code
     * // bean.name == null
     * mapper.writeValueAsString(bean); // → "name = ''"
     * }</pre>
     *
     * <p>With {@code fail-on-null-write: true}:</p>
     * <pre>{@code
     * // bean.name == null
     * mapper.writeValueAsString(bean); // → throws SerdeException
     * }</pre>
     */
    @ConfigurationProperties("write-features")
    public static final class WriteFeatures {
        private boolean failOnNullWrite;
        private WriteLayout writeLayout = WriteLayout.TABLE;

        @Bindable(defaultValue = StringUtils.FALSE)
        public boolean isFailOnNullWrite() {
            return failOnNullWrite;
        }

        public void setFailOnNullWrite(boolean failOnNullWrite) {
            this.failOnNullWrite = failOnNullWrite;
        }

        public WriteLayout getWriteLayout() {
            return writeLayout;
        }

        public void setWriteLayout(WriteLayout writeLayout) {
            this.writeLayout = Objects.requireNonNull(writeLayout, "writeLayout");
        }
    }
}
