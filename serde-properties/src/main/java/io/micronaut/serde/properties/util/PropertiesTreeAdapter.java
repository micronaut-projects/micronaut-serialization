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
import jakarta.inject.Singleton;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


/**
 * Builds an intermediate JSON tree from flat Java {@code .properties} keys.
 *
 * <p>The tree-building algorithm is adapted from Micronaut Core's
 * {@code JsonBeanPropertyBinder#buildSourceObjectNode(...)}.</p>
 *
 * @see <a href="https://github.com/micronaut-projects/micronaut-core/blob/5.0.x/json-core/src/main/java/io/micronaut/json/bind/JsonBeanPropertyBinder.java">Micronaut Core JsonBeanPropertyBinder</a>
 * @since 3.0.1
 */
@Internal
@Singleton
public class PropertiesTreeAdapter {

    private final JsonStreamMapper jsonMapper;
    private final int arraySizeThreshold;

    public PropertiesTreeAdapter(JsonStreamMapper jsonStreamMapper, DeserializationConfiguration deserializationConfiguration) {
        this.jsonMapper = jsonStreamMapper;
        this.arraySizeThreshold = deserializationConfiguration.getArraySizeThreshold();
    }

    /**
     * Parses a Java {@code .properties} stream into a JSON tree.
     *
     * @param stream
     * @return The parsed JSON tree
     * @throws IOException
     */
    public JsonNode parse(InputStream stream) throws IOException {
        AbstractPropertySourceLoader loader = new PropertiesPropertySourceLoader(false);
        Map<String, Object> read = loader.read("", stream);
        Map<CharSequence, Object> values = new LinkedHashMap<>(read);
        return buildSourceObjectNode(values.entrySet());
    }

    private JsonNode buildSourceObjectNode(Set<? extends Map.Entry<? extends CharSequence, Object>> source) throws IOException {
        var rootNode = new ObjectBuilder();
        for (Map.Entry<? extends CharSequence, ? super Object> entry : source) {
            CharSequence key = entry.getKey();
            Object value = entry.getValue();
            String property = key.toString();
            ObjectBuilder current = rootNode;
            String index = null;
            Iterator<String> tokenIterator = StringUtils.splitOmitEmptyStringsIterator(property, '.');
            while (tokenIterator.hasNext()) {
                String token = tokenIterator.next();
                int j = token.indexOf('[');
                if (j > -1 && token.endsWith("]")) {
                    index = token.substring(j + 1, token.length() - 1);
                    token = token.substring(0, j);
                }


                // We are at the last part of the .properties key.
                // Example: for "book.title", the last part is "title".
                // Example: for "values[0]", the last part is directly "values[0]".
                // So at this point we must store the value in the JSON tree.

                if (!tokenIterator.hasNext()) {
                    // Convert the raw .properties value into a JsonNode.
                    // i.E "localhost" becomes a JSON string node.
                    JsonNode valueNode = toValueNode(value);

                    // Case 1: the key ends with a numeric index, like "values[0]=a".
                    // Here index = "0", so this should be stored as a JSON array/list.
                    if (index != null && StringUtils.isDigits(index)) {
                        // Get or create the array under the key "values".
                        ArrayBuilder arrayNode = getOrCreateArrayAtKey(current, token);

                        // Convert the text index "0" into integer 0.
                        int arrayIndex = Integer.parseInt(index);

                        // Grow the array if needed so this index exists.
                        // Example: if values[2]=c arrives before values[0], missing slots are added.
                        expandArrayToThreshold(arrayIndex, arrayNode);

                        // Store the value at the correct array position.
                        // Example: values[0]=a stores "a" at position 0.
                        arrayNode.values.set(arrayIndex, new FixedValue(valueNode));

                        // Case 2: the key ends with a non-numeric index, like "authorsByInitials[SK]=...".
                        // Here index = "SK", so this should be stored as a map/object entry, not an array.
                    } else if (index != null) {
                        // Get or create the object under the key "authorsByInitials".
                        ObjectBuilder objectNode = getOrCreateObjectAtKey(current, token);

                        // Store the value using "SK" as the object/map key.
                        // Example: authorsByInitials[SK]=x becomes { "authorsByInitials": { "SK": x } }
                        objectNode.values.put(index, new FixedValue(valueNode));

                        // Case 3: there is no index.
                        // Example: "host=localhost" or "book.title=The Stand".
                    } else {
                        // Store the value directly under the property name.
                        current.values.put(token, new FixedValue(valueNode));
                    }
                } else {
                    if (index != null) {
                        if (StringUtils.isDigits(index)) {
                            ArrayBuilder arrayNode = getOrCreateArrayAtKey(current, token);
                            int arrayIndex = Integer.parseInt(index);
                            expandArrayToThreshold(arrayIndex, arrayNode);
                            current = getOrCreateNodeAtIndex(arrayNode, arrayIndex);
                        } else {
                            ObjectBuilder objectNode = getOrCreateObjectAtKey(current, token);
                            current = getOrCreateObjectAtKey(objectNode, index);
                        }
                        index = null;
                    } else {
                        current = getOrCreateObjectAtKey(current, token);
                    }
                }
            }
        }
        return rootNode.build();
    }

    // Divination - Coercion
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
