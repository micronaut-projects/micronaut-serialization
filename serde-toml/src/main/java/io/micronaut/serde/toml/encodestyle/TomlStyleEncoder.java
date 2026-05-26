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
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.toml.TomlGeneratorEncoder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Base root encoder for a specific TOML output style.
 */
@Internal
public abstract sealed class TomlStyleEncoder extends TomlGeneratorEncoder
    permits TableRootEncoder, InlineRootEncoder {
    private final OutputStream outputStream;
    @Nullable
    private JsonNode value;

    /**
     * @param outputStream The target output stream
     * @param remainingLimits The remaining encoder limits
     */
    protected TomlStyleEncoder(OutputStream outputStream,
                               LimitingStream.RemainingLimits remainingLimits) {
        super(remainingLimits, "", null);
        this.outputStream = outputStream;
    }

    @Override
    protected void acceptValue(JsonNode value) {
        if (this.value != null) {
            throw new IllegalStateException("Root TOML value already completed");
        }
        this.value = value;
    }

    @Override
    public @NonNull String currentPath() {
        return "";
    }

    @Override
    public void writeCompleted() throws IOException {
        checkChild();
        if (value == null) {
            throw new IllegalStateException("Root TOML value has not completed");
        }
        StringBuilder builder = new StringBuilder();
        appendCompletedDocument(builder, value);
        outputStream.write(builder.toString().getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    /**
     * Renders the completed TOML value tree into the target builder using the layout strategy {@link io.micronaut.serde.toml.support.SerdeTomlConfiguration} of this encoder.
     *
     * @param builder The target builder to append the TOML document to
     * @param value The root TOML value tree to render
     * @throws IOException If the value cannot be rendered as a valid TOML document
     */
    protected abstract void appendCompletedDocument(StringBuilder builder, JsonNode value) throws IOException;
}
