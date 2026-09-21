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
package io.micronaut.serde.jackson;

import io.micronaut.serde.support.patch.PatchToken;
import io.micronaut.serde.support.patch.TokenReader;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;

import java.io.IOException;

/** Jackson token adapter that retains exact number text. */
final class JacksonPatchReader implements TokenReader {
    private final JsonParser parser;
    private @Nullable PatchToken current;
    private String text = "";

    JacksonPatchReader(JsonParser parser) throws IOException {
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
            JsonToken token = parser.nextToken();
            text = "";
            if (token == null) {
                current = null;
                return;
            }
            current = switch (token) {
                case START_OBJECT -> PatchToken.START_OBJECT;
                case END_OBJECT -> PatchToken.END_OBJECT;
                case START_ARRAY -> PatchToken.START_ARRAY;
                case END_ARRAY -> PatchToken.END_ARRAY;
                case PROPERTY_NAME -> PatchToken.KEY;
                case VALUE_STRING -> PatchToken.STRING;
                case VALUE_NUMBER_INT, VALUE_NUMBER_FLOAT -> PatchToken.NUMBER;
                case VALUE_TRUE -> PatchToken.TRUE;
                case VALUE_FALSE -> PatchToken.FALSE;
                case VALUE_NULL -> PatchToken.NULL;
                default -> throw new IOException("Non-JSON token: " + token);
            };
            if (current == PatchToken.KEY || current == PatchToken.STRING || current == PatchToken.NUMBER) {
                text = parser.getText();
            }
            if ((token == JsonToken.VALUE_NUMBER_FLOAT || token == JsonToken.VALUE_NUMBER_INT) && parser.isNaN()) {
                throw new IOException("Non-finite JSON number");
            }
        } catch (StreamReadException e) {
            throw new IOException("Invalid JSON", e);
        }
    }
}
