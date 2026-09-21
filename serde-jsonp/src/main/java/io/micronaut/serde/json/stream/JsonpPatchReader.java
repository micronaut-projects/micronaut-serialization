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
package io.micronaut.serde.json.stream;

import io.micronaut.serde.support.patch.PatchToken;
import io.micronaut.serde.support.patch.TokenReader;
import jakarta.json.JsonException;
import jakarta.json.stream.JsonParser;
import org.jspecify.annotations.Nullable;

import java.io.IOException;

/** JSON-P token adapter, independent of Jackson. */
final class JsonpPatchReader implements TokenReader {
    private final JsonParser parser;
    private @Nullable PatchToken current;
    private String text = "";

    JsonpPatchReader(JsonParser parser) throws IOException {
        this.parser = parser;
        next();
    }

    @Override
    public @Nullable PatchToken current() {
        return current;
    }

    @Override
    public String text() {
        return text;
    }

    @Override
    public void next() throws IOException {
        try {
            text = "";
            if (!parser.hasNext()) {
                current = null;
                return;
            }
            JsonParser.Event event = parser.next();
            current = switch (event) {
                case START_OBJECT -> PatchToken.START_OBJECT;
                case END_OBJECT -> PatchToken.END_OBJECT;
                case START_ARRAY -> PatchToken.START_ARRAY;
                case END_ARRAY -> PatchToken.END_ARRAY;
                case KEY_NAME -> PatchToken.KEY;
                case VALUE_STRING -> PatchToken.STRING;
                case VALUE_NUMBER -> PatchToken.NUMBER;
                case VALUE_TRUE -> PatchToken.TRUE;
                case VALUE_FALSE -> PatchToken.FALSE;
                case VALUE_NULL -> PatchToken.NULL;
            };
            if (current == PatchToken.KEY || current == PatchToken.STRING || current == PatchToken.NUMBER) {
                text = parser.getString();
            }
        } catch (JsonException e) {
            throw new IOException("Invalid JSON", e);
        }
    }
}
