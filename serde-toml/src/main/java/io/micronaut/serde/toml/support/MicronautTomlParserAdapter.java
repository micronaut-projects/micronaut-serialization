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

import io.micronaut.core.annotation.Internal;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.toml.Parser;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

@Internal
public final class MicronautTomlParserAdapter {

    private static final int DEFAULT_MAX_NUMBER_LENGTH = 1000;

    @Nullable
    private final SerdeConfiguration serdeConfiguration;
    private final SerdeTomlConfiguration tomlConfiguration;

    public MicronautTomlParserAdapter(@Nullable SerdeConfiguration serdeConfiguration,
                                      SerdeTomlConfiguration tomlConfiguration) {
        this.serdeConfiguration = serdeConfiguration;
        this.tomlConfiguration = tomlConfiguration;
    }

    public @NonNull JsonNode parse(@NonNull InputStream inputStream) throws IOException {
        String toml = readUtf8(inputStream);
        JsonNode root;
        try {
            root = Parser.parse(toml);
        } catch (StackOverflowError e) {
            throw new SerdeException("Document nesting depth exceeds the maximum allowed — StackOverflowError during parsing", e);
        } catch (IOException e) {
            throw new SerdeException("Error decoding TOML: " + e.getMessage(), e);
        }
        new TomlLimitWalker(initialLimits()).walk(root);
        return root;
    }

    private LimitingStream.RemainingLimits initialLimits() {
        return serdeConfiguration == null
            ? LimitingStream.DEFAULT_LIMITS
            : LimitingStream.limitsFromConfiguration(serdeConfiguration);
    }

    private static String readUtf8(InputStream inputStream) throws IOException {
        byte[] bytes = inputStream.readAllBytes();
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            throw new SerdeException("Invalid UTF-8 TOML input", e);
        }
    }

    private static int numberLength(Number number) {
        if (number instanceof BigDecimal decimal) {
            return decimal.toPlainString().length();
        }
        return number.toString().length();
    }

    /**
     * Walks a JsonNode tree, enforcing nesting depth via LimitingStream and
     * post-hoc verifying string/number length constraints.
     */
    private final class TomlLimitWalker extends LimitingStream {
        TomlLimitWalker(RemainingLimits limits) {
            super(limits);
        }

        void walk(JsonNode node) throws SerdeException {
            if (node.isContainerNode()) {
                increaseDepth();
                try {
                    for (JsonNode child : node.values()) {
                        walk(child);
                    }
                } finally {
                    decreaseDepth();
                }
            } else if (node.isString()) {
                Integer maxStringLength = tomlConfiguration.getMaxStringLength();
                if (maxStringLength != null) {
                    int length = node.getStringValue().length();
                    if (length > maxStringLength) {
                        throw new SerdeException(
                            "String value length (" + length + ") exceeds the maximum allowed (" + maxStringLength + ")");
                    }
                }
            } else if (node.isNumber()) {
                int length = numberLength(node.getNumberValue());
                int maxNumberLength = tomlConfiguration.getMaxNumberLength() != null
                    ? tomlConfiguration.getMaxNumberLength()
                    : DEFAULT_MAX_NUMBER_LENGTH;
                if (length > maxNumberLength) {
                    throw new SerdeException(
                        "Number value length (" + length + ") exceeds the maximum allowed (" + maxNumberLength + ")");
                }
            }
        }
    }
}
