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
package io.micronaut.serde.properties.util;

import io.micronaut.context.env.AbstractPropertySourceLoader;
import io.micronaut.context.env.PropertiesPropertySourceLoader;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.json.stream.JsonStreamMapper;
import io.micronaut.serde.properties.SerdePropertiesConfiguration;
import jakarta.inject.Singleton;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds an intermediate JSON tree from flat Java {@code .properties} keys.
 *
 * <p>The tree-building algorithm is copied and adapted from Micronaut Core's
 * {@code JsonBeanPropertyBinder#buildSourceObjectNode(...)}. Bracketed array
 * indexes are the default behavior, and dotted one-based array indexes are
 * supported when configured through {@link SerdePropertiesConfiguration}.</p>
 *
 * @see <a href="https://github.com/micronaut-projects/micronaut-core/blob/5.0.x/json-core/src/main/java/io/micronaut/json/bind/JsonBeanPropertyBinder.java">Micronaut Core JsonBeanPropertyBinder</a>
 * @since 3.1.0
 */
@Internal
@Singleton
public class PropertiesTreeAdapter {

    private final JsonStreamMapper jsonMapper;
    private final int arraySizeThreshold;
    private final SerdePropertiesConfiguration.ArrayIndexStyle arrayIndexStyle;

    /**
     * Creates a properties tree adapter.
     *
     * @param jsonStreamMapper The JSON mapper used to convert non-string property values
     * @param deserializationConfiguration The deserialization configuration
     * @param propertiesConfiguration The properties format configuration
     */
    public PropertiesTreeAdapter(JsonStreamMapper jsonStreamMapper,
                                 DeserializationConfiguration deserializationConfiguration,
                                 SerdePropertiesConfiguration propertiesConfiguration) {
        this.jsonMapper = jsonStreamMapper;
        this.arraySizeThreshold = deserializationConfiguration.getArraySizeThreshold();
        this.arrayIndexStyle = propertiesConfiguration.getArrayIndexStyle();
    }

    /**
     * Parses a Java {@code .properties} stream into a JSON tree.
     *
     * @param stream The properties input stream
     * @return The parsed JSON tree
     * @throws IOException If the properties stream cannot be read
     */
    public JsonNode parse(InputStream stream) throws IOException {
        AbstractPropertySourceLoader loader = new PropertiesPropertySourceLoader(false);
        Map<String, Object> read = loader.read("", stream);
        Map<CharSequence, Object> values = new LinkedHashMap<>();
        values.putAll(read);
        return buildSourceObjectNode(values.entrySet());
    }

    private JsonNode buildSourceObjectNode(Set<? extends Map.Entry<? extends CharSequence, Object>> source) throws IOException {
        var rootNode = new ObjectBuilder();
        for (Map.Entry<? extends CharSequence, ? super Object> entry : source) {
            CharSequence key = entry.getKey();
            Object value = entry.getValue();
            ObjectBuilder current = rootNode;
            List<String> tokens = splitProperty(key.toString());
            for (int i = 0; i < tokens.size(); i++) {
                String token = tokens.get(i);
                String index = null;
                int j = token.indexOf('[');
                if (j > -1 && token.endsWith("]")) {
                    index = token.substring(j + 1, token.length() - 1);
                    token = token.substring(0, j);
                }

                if (isDottedArrayPath(index, tokens, i)) {
                    ArrayBuilder arrayNode = getOrCreateArrayAtKey(current, token);
                    int arrayIndex = toDottedArrayIndex(tokens.get(++i));
                    expandArrayToThreshold(arrayIndex, arrayNode);
                    if (hasNextToken(tokens, i)) {
                        current = getOrCreateNodeAtIndex(arrayNode, arrayIndex);
                    } else {
                        arrayNode.values.set(arrayIndex, new FixedValue(toValueNode(value)));
                    }
                    continue;
                }

                if (hasNextToken(tokens, i)) {
                    current = enterValue(current, token, index);
                } else {
                    JsonNode valueNode = toValueNode(value);
                    storeValue(current, token, index, valueNode);
                }
            }
        }
        return rootNode.build();
    }

    private List<String> splitProperty(String property) {
        List<String> tokens = new ArrayList<>();
        StringUtils.splitOmitEmptyStringsIterator(property, '.').forEachRemaining(tokens::add);
        return tokens;
    }

    private void storeValue(ObjectBuilder current, String token, @Nullable String index, JsonNode valueNode) throws SerdeException {
        if (index != null && StringUtils.isDigits(index)) {
            ArrayBuilder arrayNode = getOrCreateArrayAtKey(current, token);
            int arrayIndex = Integer.parseInt(index);
            expandArrayToThreshold(arrayIndex, arrayNode);
            arrayNode.values.set(arrayIndex, new FixedValue(valueNode));
        } else if (index != null) {
            ObjectBuilder objectNode = getOrCreateObjectAtKey(current, token);
            objectNode.values.put(index, new FixedValue(valueNode));
        } else {
            current.values.put(token, new FixedValue(valueNode));
        }
    }

    private ObjectBuilder enterValue(ObjectBuilder current, String token, @Nullable String index) throws SerdeException {
        if (index != null) {
            if (StringUtils.isDigits(index)) {
                ArrayBuilder arrayNode = getOrCreateArrayAtKey(current, token);
                int arrayIndex = Integer.parseInt(index);
                expandArrayToThreshold(arrayIndex, arrayNode);
                return getOrCreateNodeAtIndex(arrayNode, arrayIndex);
            }
            ObjectBuilder objectNode = getOrCreateObjectAtKey(current, token);
            return getOrCreateObjectAtKey(objectNode, index);
        }
        return getOrCreateObjectAtKey(current, token);
    }

    private boolean useDottedArrayIndexes() {
        return arrayIndexStyle == SerdePropertiesConfiguration.ArrayIndexStyle.DOTTED;
    }

    private boolean isDottedArrayPath(@Nullable String index, List<String> tokens, int position) {
        if (index != null) {
            return false;
        }
        if (!hasNextToken(tokens, position)) {
            return false;
        }
        if (!useDottedArrayIndexes()) {
            return false;
        }
        return isDottedArrayIndex(tokens.get(position + 1));
    }

    private boolean hasNextToken(List<String> tokens, int position) {
        return position + 1 < tokens.size();
    }

    // ("1" -> true & >0) or "name" -> false
    private boolean isDottedArrayIndex(String token) {
        return StringUtils.isDigits(token) && Integer.parseInt(token) > 0;
    }

    private int toDottedArrayIndex(String token) {
        return Integer.parseInt(token) - 1;
    }

    private JsonNode toValueNode(Object value) throws IOException {
        if (value instanceof String string) {
            return JsonNode.createStringNode(string);
        }
        return jsonMapper.writeValueToTree(value);
    }

    private ObjectBuilder getOrCreateObjectAtKey(ObjectBuilder objectNode, String key) {
        ValueBuilder valueBuilder = objectNode.values.get(key);
        if (valueBuilder instanceof ObjectBuilder objectBuilder) {
            return objectBuilder;
        }
        ObjectBuilder objectBuilder = new ObjectBuilder();
        objectNode.values.put(key, objectBuilder);
        return objectBuilder;
    }

    private ArrayBuilder getOrCreateArrayAtKey(ObjectBuilder objectNode, String key) {
        ValueBuilder valueBuilder = objectNode.values.get(key);
        if (valueBuilder instanceof ArrayBuilder arrayBuilder) {
            return arrayBuilder;
        }
        var arrayBuilder = new ArrayBuilder();
        objectNode.values.put(key, arrayBuilder);
        return arrayBuilder;
    }

    private ObjectBuilder getOrCreateNodeAtIndex(ArrayBuilder arrayNode, int arrayIndex) {
        ValueBuilder jsonNode = arrayNode.values.get(arrayIndex);
        if (jsonNode instanceof ObjectBuilder objectBuilder) {
            return objectBuilder;
        }
        var objectBuilder = new ObjectBuilder();
        arrayNode.values.set(arrayIndex, objectBuilder);
        return objectBuilder;
    }

    private void expandArrayToThreshold(int arrayIndex, ArrayBuilder arrayNode) throws SerdeException {
        if (arrayIndex >= arraySizeThreshold) {
            throw new SerdeException("Array index [" + arrayIndex + "] exceeds the configured array size threshold [" + arraySizeThreshold + "]");
        }
        while (arrayNode.values.size() < arrayIndex + 1) {
            arrayNode.values.add(FixedValue.NULL);
        }
    }

    private interface ValueBuilder {
        JsonNode build();
    }

    private static final class FixedValue implements ValueBuilder {
        static final FixedValue NULL = new FixedValue(JsonNode.nullNode());

        final JsonNode value;

        FixedValue(JsonNode value) {
            this.value = value;
        }

        @Override
        public JsonNode build() {
            return value;
        }
    }

    private static final class ObjectBuilder implements ValueBuilder {
        final Map<String, ValueBuilder> values = new LinkedHashMap<>();

        @Override
        public JsonNode build() {
            var built = CollectionUtils.<String, JsonNode>newLinkedHashMap(values.size());
            for (Map.Entry<String, ValueBuilder> entry : values.entrySet()) {
                built.put(entry.getKey(), entry.getValue().build());
            }
            return JsonNode.createObjectNode(built);
        }
    }

    private static final class ArrayBuilder implements ValueBuilder {
        final List<ValueBuilder> values = new ArrayList<>();

        @Override
        public JsonNode build() {
            return JsonNode.createArrayNode(values.stream().map(ValueBuilder::build).toList());
        }
    }
}
