package io.micronaut.serde.properties;

import io.micronaut.context.env.AbstractPropertySourceLoader;
import io.micronaut.context.env.PropertiesPropertySourceLoader;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.json.stream.JsonStreamMapper;
import jakarta.inject.Singleton;


import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Internal
@Singleton
public class PropertiesTreeAdapter {

    private final JsonStreamMapper jsonMapper;

    public PropertiesTreeAdapter(JsonStreamMapper jsonStreamMapper) {
        this.jsonMapper = jsonStreamMapper;
    }

    protected JsonNode parse(InputStream stream) throws IOException {
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

                if (!tokenIterator.hasNext()) {
                    if (index != null) {
                        current = getOrCreateObjectAtKey(current, index);
                    }

                    // Using JsonStreamMapper rather than JsonMapper that import some Jackson databind

                    JsonNode valueNode = jsonMapper.writeValueToTree(value);
                    if (current == rootNode && valueNode.isValueNode()) {
                        // Store root values as an array of a single value to
                        // simplify deserialization cases of usersId=1&usersId=2 vs usersId=1 into a collection
                        ArrayBuilder array = new ArrayBuilder();
                        array.values.add(new FixedValue(valueNode));
                        current.values.put(token, array);
                    } else {
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

    private void expandArrayToThreshold(int arrayIndex, ArrayBuilder arrayNode) {
//        if (arrayIndex < arraySizeThreshold) {
            while (arrayNode.values.size() < arrayIndex + 1) {
                arrayNode.values.add(FixedValue.NULL);
            }
//        }
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
