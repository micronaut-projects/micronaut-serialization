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
package io.micronaut.serde.toml.encodestyle;

import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.toml.entities.ArrayValue;
import io.micronaut.serde.toml.entities.BooleanValue;
import io.micronaut.serde.toml.entities.NullValue;
import io.micronaut.serde.toml.entities.NumberValue;
import io.micronaut.serde.toml.entities.ObjectValue;
import io.micronaut.serde.toml.entities.StringValue;
import io.micronaut.serde.toml.entities.TomlValue;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;

import static io.micronaut.serde.toml.encodestyle.TomlStyleRenderer.renderKeySegment;
import static io.micronaut.serde.toml.encodestyle.TomlStyleRenderer.renderString;

/**
 * Root encoder for TOML inline output style.
 */
@Internal
public final class InlineRootEncoder extends TomlStyleEncoder {

    /**
     * @param outputStream The target output stream
     * @param remainingLimits The remaining encoder limits
     * @param failOnNullWrite Whether null writing should fail
     */
    public InlineRootEncoder(OutputStream outputStream,
                             LimitingStream.RemainingLimits remainingLimits,
                             boolean failOnNullWrite) {
        super(outputStream, remainingLimits, failOnNullWrite);
    }

    @Override
    protected void appendCompletedDocument(StringBuilder builder, TomlValue value) throws IOException {
        appendInlineDocument(builder, value);
    }

    /**
     * Append a complete inline-style TOML document.
     *
     * @param builder The target builder
     * @param value The root TOML value
     * @throws IOException If the root value cannot be rendered
     */
    public static void appendInlineDocument(StringBuilder builder, TomlValue value) throws IOException {
        if (!(value instanceof ObjectValue objectValue)) {
            throw new SerdeException("TOML root value must be an object");
        }
        for (Map.Entry<String, TomlValue> entry : objectValue.values().entrySet()) {
            builder.append(renderKeySegment(entry.getKey()))
                .append(" = ")
                .append(renderInlineValue(entry.getValue()))
                .append('\n');
        }
    }

    /**
     * Render a TOML value using inline syntax.
     * <a href="https://toml.io/en/v1.0.0#inline-table">TOML inline Table Spec</a>
     *
     * @param value The value to render
     * @return The TOML inline representation
     */
    public static String renderInlineValue(TomlValue value) {
        if (value instanceof StringValue stringValue) {
            return renderString(stringValue.value());
        }
        if (value instanceof NumberValue numberValue) {
            return numberValue.value();
        }
        if (value instanceof BooleanValue booleanValue) {
            return Boolean.toString(booleanValue.value());
        }
        if (value instanceof NullValue) {
            return renderString("");
        }
        if (value instanceof ArrayValue arrayValue) {
            StringBuilder builder = new StringBuilder("[");
            for (int i = 0; i < arrayValue.values().size(); i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(renderInlineValue(arrayValue.values().get(i)));
            }
            return builder.append(']').toString();
        }
        if (value instanceof ObjectValue objectValue) {
            StringBuilder builder = new StringBuilder("{");
            int index = 0;
            for (Map.Entry<String, TomlValue> entry : objectValue.values().entrySet()) {
                if (index++ > 0) {
                    builder.append(", ");
                }
                builder.append(renderKeySegment(entry.getKey()))
                    .append(" = ")
                    .append(renderInlineValue(entry.getValue()));
            }
            return builder.append('}').toString();
        }
        throw new IllegalStateException("Unknown TOML value: " + value);
    }
}
