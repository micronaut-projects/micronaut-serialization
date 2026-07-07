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
package io.micronaut.serde.yaml;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.config.SerdeConfiguration;
import org.yaml.snakeyaml.DumperOptions;

import java.util.Objects;

/**
 * YAML-specific configuration.
 *
 * @since 3.1.0
 */
@Internal
@ConfigurationProperties(SerdeYamlConfiguration.PREFIX)
public final class SerdeYamlConfiguration {

    static final String PREFIX = SerdeConfiguration.PREFIX + ".format.yaml";

    private WriteFeatures writeFeatures = new WriteFeatures();
    private ReadFeatures readFeatures = new ReadFeatures();

    /**
     * Returns the YAML read features.
     *
     * @return The YAML read features
     */
    public ReadFeatures getReadFeatures() {
        return readFeatures;
    }

    /**
     * Sets the YAML read features.
     *
     * @param readFeatures The YAML read features
     */
    public void setReadFeatures(ReadFeatures readFeatures) {
        this.readFeatures = Objects.requireNonNull(readFeatures, "readFeatures");
    }

    /**
     * Returns the YAML write features.
     *
     * @return The YAML write features
     */
    public WriteFeatures getWriteFeatures() {
        return writeFeatures;
    }

    /**
     * Sets the YAML write features.
     *
     * @param writeFeatures The YAML write features
     */
    public void setWriteFeatures(WriteFeatures writeFeatures) {
        this.writeFeatures = Objects.requireNonNull(writeFeatures, "writeFeatures");
    }

    /**
     * Returns the configured write style.
     *
     * @return The configured write style
     */
    public WriteStyle getWriteStyle() {
        return writeFeatures.getWriteStyle();
    }

    /**
     * Returns whether to emit an explicit document start marker.
     *
     * @return Whether to emit {@code ---}
     */
    public boolean isExplicitStart() {
        return writeFeatures.isExplicitStart();
    }

    /**
     * Returns whether to emit an explicit document end marker.
     *
     * @return Whether to emit {@code ...}
     */
    public boolean isExplicitEnd() {
        return writeFeatures.isExplicitEnd();
    }

    /**
     * Returns the configured indentation width.
     *
     * @return The indentation width
     */
    public int getIndent() {
        return writeFeatures.getIndent();
    }

    /**
     * Returns whether quotes should be omitted from strings when YAML permits it.
     *
     * @return Whether to minimize quotes
     */
    public boolean isMinimizeQuotes() {
        return writeFeatures.isMinimizeQuotes();
    }

    /**
     * Returns whether multiline strings should be emitted using YAML literal block style.
     *
     * @return Whether to use literal block style
     */
    public boolean isLiteralBlockStyle() {
        return writeFeatures.isLiteralBlockStyle();
    }

    /**
     * Returns whether long textual content should be split into multiple lines.
     *
     * @return Whether long lines should be split
     */
    public boolean isSplitLines() {
        return writeFeatures.isSplitLines();
    }

    /**
     * Returns whether YAML boolean-like words should be parsed as strings.
     *
     * @return Whether boolean-like words should be parsed as strings
     */
    public boolean isBooleanAsStrings() {
        return readFeatures.isBooleanAsStrings();
    }

    /**
     * Returns whether empty YAML strings should be parsed as {@code null}.
     *
     * @return Whether empty strings should be parsed as {@code null}
     */
    public boolean isEmptyStringAsNull() {
        return readFeatures.isEmptyStringAsNull();
    }

    /**
     * Controls YAML deserialization behavior.
     *
     * @since 3.0.1
     */
    @ConfigurationProperties("read-features")
    public static final class ReadFeatures {
        private boolean booleanAsStrings = true;
        private boolean emptyStringAsNull = true;

        /**
         * Returns whether YAML boolean-like words such as {@code yes}, {@code no},
         * {@code on}, and {@code off} should be parsed as strings. The canonical
         * {@code true} and {@code false} values are still parsed as booleans.
         * This feature is enabled by default to match Jackson 3 behavior.
         *
         * @return Whether boolean-like words should be parsed as strings
         */
        public boolean isBooleanAsStrings() {
            return booleanAsStrings;
        }

        /**
         * Sets whether YAML boolean-like words should be parsed as strings.
         *
         * @param booleanAsStrings Whether boolean-like words should be parsed as strings
         */
        public void setBooleanAsStrings(boolean booleanAsStrings) {
            this.booleanAsStrings = booleanAsStrings;
        }

        /**
         * Returns whether an empty plain YAML scalar should be parsed as {@code null}.
         * This feature is enabled by default.
         *
         * @return Whether an empty plain scalar should be parsed as {@code null}
         */
        public boolean isEmptyStringAsNull() {
            return emptyStringAsNull;
        }

        /**
         * Sets whether an empty plain YAML scalar should be parsed as {@code null}.
         *
         * @param emptyStringAsNull Whether an empty plain scalar should be parsed as {@code null}
         */
        public void setEmptyStringAsNull(boolean emptyStringAsNull) {
            this.emptyStringAsNull = emptyStringAsNull;
        }
    }

    /**
     * YAML collection write style.
     *
     * @since 3.1.0
     */
    public enum WriteStyle {
        /**
         * Write collections using YAML block style.
         */
        BLOCK(DumperOptions.FlowStyle.BLOCK),

        /**
         * Write collections using YAML flow style.
         */
        FLOW(DumperOptions.FlowStyle.FLOW);

        private final DumperOptions.FlowStyle flowStyle;

        WriteStyle(DumperOptions.FlowStyle flowStyle) {
            this.flowStyle = flowStyle;
        }

        DumperOptions.FlowStyle toFlowStyle() {
            return flowStyle;
        }
    }

    /**
     * Controls YAML serialization behavior.
     *
     * @since 3.1.0
     */
    @ConfigurationProperties("write-features")
    public static final class WriteFeatures {
        private WriteStyle writeStyle = WriteStyle.BLOCK;
        private int indent = 2;
        private boolean explicitStart;
        private boolean explicitEnd;
        private boolean minimizeQuotes = true;
        private boolean literalBlockStyle;
        private boolean splitLines = true;

        /**
         * Returns the configured write style.
         *
         * @return The configured write style
         */
        public WriteStyle getWriteStyle() {
            return writeStyle;
        }

        /**
         * Sets the write style.
         *
         * @param writeStyle The write style
         */
        public void setWriteStyle(WriteStyle writeStyle) {
            this.writeStyle = Objects.requireNonNull(writeStyle, "writeStyle");
        }

        /**
         * Returns the configured indentation width.
         *
         * @return The indentation width
         */
        public int getIndent() {
            return indent;
        }

        /**
         * Sets the indentation width.
         *
         * @param indent The indentation width
         */
        public void setIndent(int indent) {
            this.indent = indent;
        }

        /**
         * Returns whether to emit an explicit document start marker.
         *
         * @return Whether to emit {@code ---}
         */
        public boolean isExplicitStart() {
            return explicitStart;
        }

        /**
         * Sets whether to emit an explicit document start marker.
         *
         * @param explicitStart Whether to emit {@code ---}
         */
        public void setExplicitStart(boolean explicitStart) {
            this.explicitStart = explicitStart;
        }

        /**
         * Returns whether to emit an explicit document end marker.
         *
         * @return Whether to emit {@code ...}
         */
        public boolean isExplicitEnd() {
            return explicitEnd;
        }

        /**
         * Sets whether to emit an explicit document end marker.
         *
         * @param explicitEnd Whether to emit {@code ...}
         */
        public void setExplicitEnd(boolean explicitEnd) {
            this.explicitEnd = explicitEnd;
        }

        /**
         * Returns whether quotes should be omitted from strings when YAML permits it.
         *
         * @return Whether to minimize quotes
         */
        public boolean isMinimizeQuotes() {
            return minimizeQuotes;
        }

        /**
         * Sets whether quotes should be omitted from strings when YAML permits it.
         *
         * @param minimizeQuotes Whether to minimize quotes
         */
        public void setMinimizeQuotes(boolean minimizeQuotes) {
            this.minimizeQuotes = minimizeQuotes;
        }

        /**
         * Returns whether multiline strings should be emitted using YAML literal block style.
         *
         * @return Whether to use literal block style
         */
        public boolean isLiteralBlockStyle() {
            return literalBlockStyle;
        }

        /**
         * Sets whether multiline strings should be emitted using YAML literal block style.
         *
         * @param literalBlockStyle Whether to use literal block style
         */
        public void setLiteralBlockStyle(boolean literalBlockStyle) {
            this.literalBlockStyle = literalBlockStyle;
        }

        /**
         * Returns whether long textual content should be split into multiple lines.
         *
         * @return Whether long lines should be split
         */
        public boolean isSplitLines() {
            return splitLines;
        }

        /**
         * Sets whether long textual content should be split into multiple lines.
         *
         * @param splitLines Whether long lines should be split
         */
        public void setSplitLines(boolean splitLines) {
            this.splitLines = splitLines;
        }
    }
}
