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
package io.micronaut.serde.jsonp;

import io.micronaut.core.annotation.AnnotationMetadata;
import jakarta.json.JsonArray;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonException;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonPointer;
import jakarta.json.JsonString;
import jakarta.json.JsonStructure;
import jakarta.json.JsonValue;
import org.jspecify.annotations.Nullable;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.AbstractList;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static io.micronaut.serde.jsonp.MicronautJsonProvider.VALUE;

/**
 * JSON-P value, builder, and pointer support used by {@link MicronautJsonProvider}.
 */
final class JsonpValueSupport {
    private JsonpValueSupport() {
    }

    static final class ObjectBuilder implements JsonObjectBuilder {
        private final Map<String, JsonValue> values = new LinkedHashMap<>();

        /**
         * Adds a normalized JSON-P value to the builder state and returns the mutable builder.
         */
        @Override
        public JsonObjectBuilder add(String name, JsonValue value) {
            values.put(Objects.requireNonNull(name, "name"), Objects.requireNonNull(value, VALUE));
            return this;
        }

        /**
         * Adds a normalized JSON-P value to the builder state and returns the mutable builder.
         */
        @Override
        public JsonObjectBuilder add(String name, String value) {
            return add(name, new JsonStringValue(Objects.requireNonNull(value, VALUE)));
        }

        /**
         * Adds a normalized JSON-P value to the builder state and returns the mutable builder.
         */
        @Override
        public JsonObjectBuilder add(String name, BigInteger value) {
            return add(name, new JsonNumberValue(new BigDecimal(value)));
        }

        /**
         * Adds a normalized JSON-P value to the builder state and returns the mutable builder.
         */
        @Override
        public JsonObjectBuilder add(String name, BigDecimal value) {
            return add(name, new JsonNumberValue(Objects.requireNonNull(value, VALUE)));
        }

        /**
         * Adds a normalized JSON-P value to the builder state and returns the mutable builder.
         */
        @Override
        public JsonObjectBuilder add(String name, int value) {
            return add(name, new JsonNumberValue(BigDecimal.valueOf(value)));
        }

        /**
         * Adds a normalized JSON-P value to the builder state and returns the mutable builder.
         */
        @Override
        public JsonObjectBuilder add(String name, long value) {
            return add(name, new JsonNumberValue(BigDecimal.valueOf(value)));
        }

        /**
         * Adds a normalized JSON-P value to the builder state and returns the mutable builder.
         */
        @Override
        public JsonObjectBuilder add(String name, double value) {
            return add(name, new JsonNumberValue(BigDecimal.valueOf(value)));
        }

        /**
         * Adds a normalized JSON-P value to the builder state and returns the mutable builder.
         */
        @Override
        public JsonObjectBuilder add(String name, boolean value) {
            return add(name, value ? JsonValue.TRUE : JsonValue.FALSE);
        }

        /**
         * Adds the JSON-P null sentinel to the builder state.
         */
        @Override
        public JsonObjectBuilder addNull(String name) {
            return add(name, JsonValue.NULL);
        }

        /**
         * Adds a normalized JSON-P value to the builder state and returns the mutable builder.
         */
        @Override
        public JsonObjectBuilder add(String name, JsonObjectBuilder builder) {
            return add(name, builder.build());
        }

        /**
         * Adds a normalized JSON-P value to the builder state and returns the mutable builder.
         */
        @Override
        public JsonObjectBuilder add(String name, JsonArrayBuilder builder) {
            return add(name, builder.build());
        }

        /**
         * Copies built values from another builder into this builder's mutable state.
         */
        @Override
        public JsonObjectBuilder addAll(JsonObjectBuilder builder) {
            values.putAll(builder.build());
            return this;
        }

        /**
         * Removes the addressed value from mutable builder or pointer state.
         */
        @Override
        public JsonObjectBuilder remove(String name) {
            values.remove(Objects.requireNonNull(name, "name"));
            return this;
        }

        /**
         * Builds an immutable JSON-P value and clears mutable builder state for reuse.
         */
        @Override
        public JsonObject build() {
            JsonObjectValue built = new JsonObjectValue(values);
            values.clear();
            return built;
        }
    }

    static final class ArrayBuilder implements JsonArrayBuilder {
        private final List<JsonValue> values = new ArrayList<>();

        /**
         * Adds a normalized JSON-P value to the builder state and returns the mutable builder.
         */
        @Override
        public JsonArrayBuilder add(JsonValue value) {
            values.add(Objects.requireNonNull(value, VALUE));
            return this;
        }

        /**
         * Adds a normalized JSON-P value to the builder state and returns the mutable builder.
         */
        @Override
        public JsonArrayBuilder add(String value) {
            return add(new JsonStringValue(Objects.requireNonNull(value, VALUE)));
        }

        /**
         * Adds a normalized JSON-P value to the builder state and returns the mutable builder.
         */
        @Override
        public JsonArrayBuilder add(BigDecimal value) {
            return add(new JsonNumberValue(Objects.requireNonNull(value, VALUE)));
        }

        /**
         * Adds a normalized JSON-P value to the builder state and returns the mutable builder.
         */
        @Override
        public JsonArrayBuilder add(BigInteger value) {
            return add(new JsonNumberValue(new BigDecimal(value)));
        }

        /**
         * Adds a normalized JSON-P value to the builder state and returns the mutable builder.
         */
        @Override
        public JsonArrayBuilder add(int value) {
            return add(new JsonNumberValue(BigDecimal.valueOf(value)));
        }

        /**
         * Adds a normalized JSON-P value to the builder state and returns the mutable builder.
         */
        @Override
        public JsonArrayBuilder add(long value) {
            return add(new JsonNumberValue(BigDecimal.valueOf(value)));
        }

        /**
         * Adds a normalized JSON-P value to the builder state and returns the mutable builder.
         */
        @Override
        public JsonArrayBuilder add(double value) {
            return add(new JsonNumberValue(BigDecimal.valueOf(value)));
        }

        /**
         * Adds a normalized JSON-P value to the builder state and returns the mutable builder.
         */
        @Override
        public JsonArrayBuilder add(boolean value) {
            return add(value ? JsonValue.TRUE : JsonValue.FALSE);
        }

        /**
         * Adds the JSON-P null sentinel to the builder state.
         */
        @Override
        public JsonArrayBuilder addNull() {
            return add(JsonValue.NULL);
        }

        /**
         * Adds a normalized JSON-P value to the builder state and returns the mutable builder.
         */
        @Override
        public JsonArrayBuilder add(JsonObjectBuilder builder) {
            return add(builder.build());
        }

        /**
         * Adds a normalized JSON-P value to the builder state and returns the mutable builder.
         */
        @Override
        public JsonArrayBuilder add(JsonArrayBuilder builder) {
            return add(builder.build());
        }

        /**
         * Copies built values from another builder into this builder's mutable state.
         */
        @Override
        public JsonArrayBuilder addAll(JsonArrayBuilder builder) {
            values.addAll(builder.build());
            return this;
        }

        /**
         * Adds a normalized JSON-P value to the builder state and returns the mutable builder.
         */
        @Override
        public JsonArrayBuilder add(int index, JsonValue value) {
            values.add(index, Objects.requireNonNull(value, AnnotationMetadata.VALUE_MEMBER));
            return this;
        }

        /**
         * Adds a normalized JSON-P value to the builder state and returns the mutable builder.
         */
        @Override
        public JsonArrayBuilder add(int index, String value) {
            return add(index, new JsonStringValue(Objects.requireNonNull(value, AnnotationMetadata.VALUE_MEMBER)));
        }

        /**
         * Adds a normalized JSON-P value to the builder state and returns the mutable builder.
         */
        @Override
        public JsonArrayBuilder add(int index, BigDecimal value) {
            return add(index, new JsonNumberValue(Objects.requireNonNull(value, AnnotationMetadata.VALUE_MEMBER)));
        }

        /**
         * Adds a normalized JSON-P value to the builder state and returns the mutable builder.
         */
        @Override
        public JsonArrayBuilder add(int index, BigInteger value) {
            return add(index, new JsonNumberValue(new BigDecimal(value)));
        }

        /**
         * Adds a normalized JSON-P value to the builder state and returns the mutable builder.
         */
        @Override
        public JsonArrayBuilder add(int index, int value) {
            return add(index, new JsonNumberValue(BigDecimal.valueOf(value)));
        }

        /**
         * Adds a normalized JSON-P value to the builder state and returns the mutable builder.
         */
        @Override
        public JsonArrayBuilder add(int index, long value) {
            return add(index, new JsonNumberValue(BigDecimal.valueOf(value)));
        }

        /**
         * Adds a normalized JSON-P value to the builder state and returns the mutable builder.
         */
        @Override
        public JsonArrayBuilder add(int index, double value) {
            return add(index, new JsonNumberValue(BigDecimal.valueOf(value)));
        }

        /**
         * Adds a normalized JSON-P value to the builder state and returns the mutable builder.
         */
        @Override
        public JsonArrayBuilder add(int index, boolean value) {
            return add(index, value ? JsonValue.TRUE : JsonValue.FALSE);
        }

        /**
         * Adds the JSON-P null sentinel to the builder state.
         */
        @Override
        public JsonArrayBuilder addNull(int index) {
            return add(index, JsonValue.NULL);
        }

        /**
         * Adds a normalized JSON-P value to the builder state and returns the mutable builder.
         */
        @Override
        public JsonArrayBuilder add(int index, JsonObjectBuilder builder) {
            return add(index, builder.build());
        }

        /**
         * Adds a normalized JSON-P value to the builder state and returns the mutable builder.
         */
        @Override
        public JsonArrayBuilder add(int index, JsonArrayBuilder builder) {
            return add(index, builder.build());
        }

        /**
         * Replaces an indexed array-builder value with a normalized JSON-P value.
         */
        @Override
        public JsonArrayBuilder set(int index, JsonValue value) {
            values.set(index, Objects.requireNonNull(value, AnnotationMetadata.VALUE_MEMBER));
            return this;
        }

        /**
         * Replaces an indexed array-builder value with a normalized JSON-P value.
         */
        @Override
        public JsonArrayBuilder set(int index, String value) {
            return set(index, new JsonStringValue(Objects.requireNonNull(value, AnnotationMetadata.VALUE_MEMBER)));
        }

        /**
         * Replaces an indexed array-builder value with a normalized JSON-P value.
         */
        @Override
        public JsonArrayBuilder set(int index, BigDecimal value) {
            return set(index, new JsonNumberValue(Objects.requireNonNull(value, AnnotationMetadata.VALUE_MEMBER)));
        }

        /**
         * Replaces an indexed array-builder value with a normalized JSON-P value.
         */
        @Override
        public JsonArrayBuilder set(int index, BigInteger value) {
            return set(index, new JsonNumberValue(new BigDecimal(value)));
        }

        /**
         * Replaces an indexed array-builder value with a normalized JSON-P value.
         */
        @Override
        public JsonArrayBuilder set(int index, int value) {
            return set(index, new JsonNumberValue(BigDecimal.valueOf(value)));
        }

        /**
         * Replaces an indexed array-builder value with a normalized JSON-P value.
         */
        @Override
        public JsonArrayBuilder set(int index, long value) {
            return set(index, new JsonNumberValue(BigDecimal.valueOf(value)));
        }

        /**
         * Replaces an indexed array-builder value with a normalized JSON-P value.
         */
        @Override
        public JsonArrayBuilder set(int index, double value) {
            return set(index, new JsonNumberValue(BigDecimal.valueOf(value)));
        }

        /**
         * Replaces an indexed array-builder value with a normalized JSON-P value.
         */
        @Override
        public JsonArrayBuilder set(int index, boolean value) {
            return set(index, value ? JsonValue.TRUE : JsonValue.FALSE);
        }

        /**
         * Replaces an indexed array-builder value with the JSON-P null sentinel.
         */
        @Override
        public JsonArrayBuilder setNull(int index) {
            return set(index, JsonValue.NULL);
        }

        /**
         * Replaces an indexed array-builder value with a normalized JSON-P value.
         */
        @Override
        public JsonArrayBuilder set(int index, JsonObjectBuilder builder) {
            return set(index, builder.build());
        }

        /**
         * Replaces an indexed array-builder value with a normalized JSON-P value.
         */
        @Override
        public JsonArrayBuilder set(int index, JsonArrayBuilder builder) {
            return set(index, builder.build());
        }

        /**
         * Removes the addressed value from mutable builder or pointer state.
         */
        @Override
        public JsonArrayBuilder remove(int index) {
            values.remove(index);
            return this;
        }

        /**
         * Builds an immutable JSON-P value and clears mutable builder state for reuse.
         */
        @Override
        public JsonArray build() {
            JsonArrayValue built = new JsonArrayValue(values);
            values.clear();
            return built;
        }
    }

    static final class JsonObjectValue extends AbstractMap<String, JsonValue> implements JsonObject {
        private final Map<String, JsonValue> members;

        /**
         * Initializes this JSON-P support component with the state it owns.
         */
        JsonObjectValue(Map<String, JsonValue> values) {
            this.members = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        }

        /**
         * Exposes immutable object members through the Map view required by JsonObject.
         */
        @Override
        public Set<Entry<String, JsonValue>> entrySet() {
            return members.entrySet();
        }

        /**
         * Returns the raw immutable value from this JSON-P container.
         */
        @Override
        public @Nullable JsonValue get(Object key) {
            return members.get(key);
        }

        /**
         * Checks member presence against the immutable object backing map.
         */
        @Override
        public boolean containsKey(Object key) {
            return members.containsKey(key);
        }

        /**
         * Returns the addressed value cast to JsonArray according to the JSON-P typed accessor contract.
         */
        @Override
        public @Nullable JsonArray getJsonArray(String name) {
            return (JsonArray) members.get(name);
        }

        /**
         * Returns the addressed value cast to JsonObject according to the JSON-P typed accessor contract.
         */
        @Override
        public @Nullable JsonObject getJsonObject(String name) {
            return (JsonObject) members.get(name);
        }

        /**
         * Returns the addressed value cast to JsonNumber according to the JSON-P typed accessor contract.
         */
        @Override
        public @Nullable JsonNumber getJsonNumber(String name) {
            return (JsonNumber) members.get(name);
        }

        /**
         * Returns the addressed value cast to JsonString according to the JSON-P typed accessor contract.
         */
        @Override
        public @Nullable JsonString getJsonString(String name) {
            return (JsonString) members.get(name);
        }

        /**
         * Returns the named object member as a string, failing when the member is absent or not a string.
         */
        @Override
        public String getString(String name) {
            JsonString value = getJsonString(name);
            if (value == null) {
                throw new NullPointerException("No string value for name: " + name);
            }
            return value.getString();
        }

        /**
         * Returns the named string member, or the supplied default when the member is absent or not a string.
         */
        @Override
        public String getString(String name, String defaultValue) {
            JsonValue value = members.get(name);
            return value instanceof JsonString string ? string.getString() : defaultValue;
        }

        /**
         * Returns the named object member as an int, failing when the member is absent or not numeric.
         */
        @Override
        public int getInt(String name) {
            JsonNumber value = getJsonNumber(name);
            if (value == null) {
                throw new NullPointerException("No number value for name: " + name);
            }
            return value.intValue();
        }

        /**
         * Returns the named numeric member as an int, or the supplied default when it is absent or not numeric.
         */
        @Override
        public int getInt(String name, int defaultValue) {
            JsonValue value = members.get(name);
            return value instanceof JsonNumber number ? number.intValue() : defaultValue;
        }

        /**
         * Returns a JSON-P boolean sentinel or the supplied default when supported by the accessor contract.
         */
        @Override
        public boolean getBoolean(String name) {
            JsonValue value = members.get(name);
            if (value == null) {
                throw new NullPointerException("No boolean value for name: " + name);
            }
            if (value == JsonValue.TRUE) {
                return true;
            }
            if (value == JsonValue.FALSE) {
                return false;
            }
            throw new ClassCastException("Value is not a boolean");
        }

        /**
         * Returns a JSON-P boolean sentinel or the supplied default when supported by the accessor contract.
         */
        @Override
        public boolean getBoolean(String name, boolean defaultValue) {
            JsonValue value = members.get(name);
            if (value == JsonValue.TRUE) {
                return true;
            }
            if (value == JsonValue.FALSE) {
                return false;
            }
            return defaultValue;
        }

        /**
         * Checks whether the addressed immutable value is the JSON-P null sentinel.
         */
        @Override
        public boolean isNull(String name) {
            if (!members.containsKey(name)) {
                throw new NullPointerException("No value for name: " + name);
            }
            return members.get(name) == JsonValue.NULL;
        }

        /**
         * Returns the Jakarta JSON-P value type represented by this immutable value.
         */
        @Override
        public ValueType getValueType() {
            return ValueType.OBJECT;
        }

        /**
         * Serializes this immutable JSON-P value through the provider writer to preserve escaping rules.
         */
        @Override
        public String toString() {
            StringWriter writer = new StringWriter();
            new MicronautJsonProvider().createWriter(writer).writeObject(this);
            return writer.toString();
        }
    }

    static final class JsonArrayValue extends AbstractList<JsonValue> implements JsonArray {
        private final List<JsonValue> values;

        /**
         * Initializes this JSON-P support component with the state it owns.
         */
        JsonArrayValue(List<JsonValue> values) {
            this.values = List.copyOf(values);
        }

        /**
         * Returns the raw immutable value from this JSON-P container.
         */
        @Override
        public JsonValue get(int index) {
            return values.get(index);
        }

        /**
         * Returns the number of immutable values in this JSON-P array.
         */
        @Override
        public int size() {
            return values.size();
        }

        /**
         * Returns the addressed value cast to JsonObject according to the JSON-P typed accessor contract.
         */
        @Override
        public JsonObject getJsonObject(int index) {
            return (JsonObject) values.get(index);
        }

        /**
         * Returns the addressed value cast to JsonArray according to the JSON-P typed accessor contract.
         */
        @Override
        public JsonArray getJsonArray(int index) {
            return (JsonArray) values.get(index);
        }

        /**
         * Returns the addressed value cast to JsonNumber according to the JSON-P typed accessor contract.
         */
        @Override
        public JsonNumber getJsonNumber(int index) {
            return (JsonNumber) values.get(index);
        }

        /**
         * Returns the addressed value cast to JsonString according to the JSON-P typed accessor contract.
         */
        @Override
        public JsonString getJsonString(int index) {
            return (JsonString) values.get(index);
        }

        /**
         * Returns the immutable array view with the caller-requested JSON-P value element type.
         */
        @Override
        public <T extends JsonValue> List<T> getValuesAs(Class<T> clazz) {
            Objects.requireNonNull(clazz, "clazz");
            @SuppressWarnings("unchecked")
            List<T> result = (List<T>) values;
            return result;
        }

        /**
         * Returns the indexed array value as a string according to the JSON-P typed accessor contract.
         */
        @Override
        public String getString(int index) {
            return getJsonString(index).getString();
        }

        /**
         * Returns the indexed string value, or the supplied default when the index is absent or not a string.
         */
        @Override
        public String getString(int index, String defaultValue) {
            JsonValue value = index >= 0 && index < values.size() ? values.get(index) : null;
            return value instanceof JsonString string ? string.getString() : defaultValue;
        }

        /**
         * Returns the indexed array value as an int according to the JSON-P typed accessor contract.
         */
        @Override
        public int getInt(int index) {
            return getJsonNumber(index).intValue();
        }

        /**
         * Returns the indexed numeric value as an int, or the supplied default when absent or not numeric.
         */
        @Override
        public int getInt(int index, int defaultValue) {
            JsonValue value = index >= 0 && index < values.size() ? values.get(index) : null;
            return value instanceof JsonNumber number ? number.intValue() : defaultValue;
        }

        /**
         * Returns a JSON-P boolean sentinel or the supplied default when supported by the accessor contract.
         */
        @Override
        public boolean getBoolean(int index) {
            JsonValue value = values.get(index);
            if (value == JsonValue.TRUE) {
                return true;
            }
            if (value == JsonValue.FALSE) {
                return false;
            }
            throw new ClassCastException("Value is not a boolean");
        }

        /**
         * Returns a JSON-P boolean sentinel or the supplied default when supported by the accessor contract.
         */
        @Override
        public boolean getBoolean(int index, boolean defaultValue) {
            JsonValue value = index >= 0 && index < values.size() ? values.get(index) : null;
            if (value == JsonValue.TRUE) {
                return true;
            }
            if (value == JsonValue.FALSE) {
                return false;
            }
            return defaultValue;
        }

        /**
         * Checks whether the addressed immutable value is the JSON-P null sentinel.
         */
        @Override
        public boolean isNull(int index) {
            return values.get(index) == JsonValue.NULL;
        }

        /**
         * Returns the Jakarta JSON-P value type represented by this immutable value.
         */
        @Override
        public ValueType getValueType() {
            return ValueType.ARRAY;
        }

        /**
         * Serializes this immutable JSON-P value through the provider writer to preserve escaping rules.
         */
        @Override
        public String toString() {
            StringWriter writer = new StringWriter();
            new MicronautJsonProvider().createWriter(writer).writeArray(this);
            return writer.toString();
        }
    }

    record JsonStringValue(String getString) implements JsonString {
        /**
         * Returns the string content as a character sequence without allocating a copy.
         */
        @Override
        public CharSequence getChars() {
            return getString;
        }

        /**
         * Returns the Jakarta JSON-P value type represented by this immutable value.
         */
        @Override
        public ValueType getValueType() {
            return ValueType.STRING;
        }

        /**
         * Serializes this immutable JSON-P value through the provider writer to preserve escaping rules.
         */
        @Override
        public String toString() {
            StringWriter writer = new StringWriter();
            new MicronautJsonProvider().createGenerator(writer).write(getString).close();
            return writer.toString();
        }

        /**
         * Compares using Jakarta JSON-P value equality semantics for this scalar value.
         */
        @Override
        public boolean equals(@Nullable Object object) {
            return object instanceof JsonString jsonString && getString.equals(jsonString.getString());
        }

        /**
         * Computes a hash code aligned with this scalar value's JSON-P equality semantics.
         */
        @Override
        public int hashCode() {
            return getString.hashCode();
        }
    }

    record JsonNumberValue(BigDecimal bigDecimalValue) implements JsonNumber {
        /**
         * Reports whether the stored decimal has no fractional scale.
         */
        @Override
        public boolean isIntegral() {
            return bigDecimalValue.scale() <= 0;
        }

        /**
         * Converts the stored JSON number using BigDecimal's standard narrowing conversion.
         */
        @Override
        public int intValue() {
            return bigDecimalValue.intValue();
        }

        /**
         * Converts the stored JSON number exactly and lets BigDecimal report non-integral or overflow cases.
         */
        @Override
        public int intValueExact() {
            return bigDecimalValue.intValueExact();
        }

        /**
         * Converts the stored JSON number using BigDecimal's standard narrowing conversion.
         */
        @Override
        public long longValue() {
            return bigDecimalValue.longValue();
        }

        /**
         * Converts the stored JSON number exactly and lets BigDecimal report non-integral or overflow cases.
         */
        @Override
        public long longValueExact() {
            return bigDecimalValue.longValueExact();
        }

        /**
         * Converts the stored JSON number to BigInteger using BigDecimal truncation semantics.
         */
        @Override
        public BigInteger bigIntegerValue() {
            return bigDecimalValue.toBigInteger();
        }

        /**
         * Converts the stored JSON number exactly and lets BigDecimal report non-integral or overflow cases.
         */
        @Override
        public BigInteger bigIntegerValueExact() {
            return bigDecimalValue.toBigIntegerExact();
        }

        /**
         * Converts the stored JSON number using BigDecimal's standard narrowing conversion.
         */
        @Override
        public double doubleValue() {
            return bigDecimalValue.doubleValue();
        }

        /**
         * Returns the most precise standard Number representation for the stored JSON number.
         */
        @Override
        public Number numberValue() {
            return isIntegral() ? bigIntegerValue() : bigDecimalValue;
        }

        /**
         * Returns the Jakarta JSON-P value type represented by this immutable value.
         */
        @Override
        public ValueType getValueType() {
            return ValueType.NUMBER;
        }

        /**
         * Serializes this immutable JSON-P value through the provider writer to preserve escaping rules.
         */
        @Override
        public String toString() {
            return bigDecimalValue.toString();
        }

        /**
         * Compares using Jakarta JSON-P value equality semantics for this scalar value.
         */
        @Override
        public boolean equals(@Nullable Object object) {
            return object instanceof JsonNumber jsonNumber && bigDecimalValue.compareTo(jsonNumber.bigDecimalValue()) == 0;
        }

        /**
         * Computes a hash code aligned with this scalar value's JSON-P equality semantics.
         */
        @Override
        public int hashCode() {
            return bigDecimalValue.stripTrailingZeros().hashCode();
        }
    }

    static final class Values {
        /**
         * Normalizes Java scalar, map, and collection values into this provider's immutable JSON-P model.
         */
        static JsonValue from(@Nullable Object value) {
            switch (value) {
                case null -> {
                    return JsonValue.NULL;
                }
                case JsonValue jsonValue -> {
                    return jsonValue;
                }
                case String string -> {
                    return new JsonStringValue(string);
                }
                case Number number -> {
                    return number(number);
                }
                case Boolean bool -> {
                    return bool ? JsonValue.TRUE : JsonValue.FALSE;
                }
                case Map<?, ?> map -> {
                    ObjectBuilder builder = new ObjectBuilder();
                    map.forEach((key, item) -> builder.add(String.valueOf(key), from(item)));
                    return builder.build();
                }
                case Collection<?> collection -> {
                    ArrayBuilder builder = new ArrayBuilder();
                    collection.forEach(item -> builder.add(from(item)));
                    return builder.build();
                }
                default -> {
                }
            }
            throw new JsonException("Unsupported JSON value type: " + value.getClass().getName());
        }

        /**
         * Normalizes Java Number implementations into the provider's BigDecimal-backed JsonNumber model.
         */
        static JsonNumber number(Number number) {
            if (number instanceof BigDecimal bigDecimal) {
                return new JsonNumberValue(bigDecimal);
            }
            if (number instanceof BigInteger bigInteger) {
                return new JsonNumberValue(new BigDecimal(bigInteger));
            }
            if (number instanceof Byte || number instanceof Short || number instanceof Integer || number instanceof Long) {
                return new JsonNumberValue(BigDecimal.valueOf(number.longValue()));
            }
            if (number instanceof Float || number instanceof Double) {
                return new JsonNumberValue(BigDecimal.valueOf(number.doubleValue()));
            }
            return new JsonNumberValue(new BigDecimal(number.toString()));
        }
    }

    static final class Pointer implements JsonPointer {
        private final String source;
        private final List<String> tokens;

        /**
         * Initializes pointer tokens and validates the JSON Pointer source syntax.
         */
        Pointer(String pointer) {
            if (!pointer.isEmpty() && !pointer.startsWith("/")) {
                throw new JsonException("JSON pointer must be empty or start with '/'");
            }
            this.source = pointer;
            this.tokens = pointer.isEmpty() ? List.of() : Stream.of(pointer.substring(1).split("/", -1)).map(Pointer::unescape).toList();
        }

        /**
         * Adds a normalized JSON-P value to the builder state and returns the mutable builder.
         */
        @Override
        public <T extends JsonStructure> T add(T target, JsonValue value) {
            return update(target, value, Operation.ADD);
        }

        /**
         * Removes the addressed value from mutable builder or pointer state.
         */
        @Override
        public <T extends JsonStructure> T remove(T target) {
            return update(target, JsonValue.NULL, Operation.REMOVE);
        }

        /**
         * Replaces the pointer target with the supplied JSON-P value.
         */
        @Override
        public <T extends JsonStructure> T replace(T target, JsonValue value) {
            return update(target, value, Operation.REPLACE);
        }

        /**
         * Checks pointer resolution without leaking the exception used for failed traversal.
         */
        @Override
        public boolean containsValue(JsonStructure target) {
            try {
                getValue(target);
                return true;
            } catch (JsonException e) {
                return false;
            }
        }

        /**
         * Materializes the current value, advancing past a key event when necessary.
         */
        @Override
        public JsonValue getValue(JsonStructure target) {
            JsonValue current = target;
            for (String token : tokens) {
                if (current instanceof JsonObject object && object.containsKey(token)) {
                    current = object.get(token);
                } else if (current instanceof JsonArray array) {
                    int index = parseIndex(token, array.size());
                    current = array.get(index);
                } else {
                    throw new JsonException("JSON pointer does not resolve: " + source);
                }
            }
            return current;
        }

        /**
         * Serializes this immutable JSON-P value through the provider writer to preserve escaping rules.
         */
        @Override
        public String toString() {
            return source;
        }

        /**
         * Applies a pointer mutation and returns a rebuilt immutable root structure.
         */
        @SuppressWarnings("unchecked")
        private <T extends JsonStructure> T update(T target, JsonValue value, Operation operation) {
            if (tokens.isEmpty()) {
                if (operation == Operation.REMOVE) {
                    throw new JsonException("Cannot remove document root");
                }
                return (T) requireStructure(value);
            }
            return (T) updateValue(target, value, operation);
        }

        /**
         * Traverses to the pointer parent while collecting immutable rebuild frames.
         */
        private JsonValue updateValue(JsonValue current, JsonValue value, Operation operation) {
            List<PathFrame> frames = new ArrayList<>(tokens.size() - 1);
            for (int i = 0; i < tokens.size() - 1; i++) {
                String token = tokens.get(i);
                if (current instanceof JsonObject object) {
                    if (!object.containsKey(token)) {
                        throw new JsonException("JSON pointer does not resolve: " + source);
                    }
                    frames.add(new ObjectFrame(object, token));
                    current = object.get(token);
                } else if (current instanceof JsonArray array) {
                    int index = parseIndex(token, array.size());
                    frames.add(new ArrayFrame(array, index));
                    current = array.get(index);
                } else {
                    throw new JsonException("JSON pointer does not resolve: " + source);
                }
            }

            JsonValue updated = updateLeaf(current, tokens.getLast(), value, operation);
            for (int i = frames.size() - 1; i >= 0; i--) {
                updated = frames.get(i).withUpdatedChild(updated);
            }
            return updated;
        }

        /**
         * Applies the pointer operation to the final object member or array element.
         */
        @SuppressWarnings("java:S3776")
        private JsonValue updateLeaf(JsonValue current, String token, JsonValue value, Operation operation) {
            if (current instanceof JsonObject object) {
                Map<String, JsonValue> copy = new LinkedHashMap<>(object);
                if (operation == Operation.REMOVE) {
                    if (copy.remove(token) == null) {
                        throw new JsonException("JSON pointer does not resolve: " + source);
                    }
                } else {
                    if (operation == Operation.REPLACE && !copy.containsKey(token)) {
                        throw new JsonException("JSON pointer does not resolve: " + source);
                    }
                    copy.put(token, value);
                }
                return new JsonObjectValue(copy);
            }
            if (current instanceof JsonArray array) {
                List<JsonValue> copy = new ArrayList<>(array);
                int index = "-".equals(token) ? copy.size() : parseIndex(token, copy.size(), operation == Operation.ADD);
                try {
                    switch (operation) {
                        case ADD -> copy.add(index, value);
                        case REPLACE -> copy.set(index, value);
                        case REMOVE -> copy.remove(index);
                        default -> throw new JsonException("Unsupported JSON pointer operation: " + operation);
                    }
                } catch (IndexOutOfBoundsException e) {
                    throw new JsonException("JSON pointer array index is out of bounds: " + token, e);
                }
                return new JsonArrayValue(copy);
            }
            throw new JsonException("JSON pointer does not resolve: " + source);
        }

        /**
         * Validates that root replacement produces a JSON-P structure as required by JsonPointer.
         */
        private static JsonStructure requireStructure(JsonValue value) {
            if (value instanceof JsonStructure structure) {
                return structure;
            }
            throw new JsonException("JSON pointer root replacement must be an object or array");
        }

        /**
         * Parses and bounds-checks a JSON Pointer array index token.
         */
        private static int parseIndex(String token, int size) {
            return parseIndex(token, size, false);
        }

        /**
         * Parses and bounds-checks a JSON Pointer array index token.
         */
        private static int parseIndex(String token, int size, boolean allowEnd) {
            try {
                int index = Integer.parseInt(token);
                if (index < 0 || index > size || (!allowEnd && index == size)) {
                    throw new JsonException("JSON pointer array index is out of bounds: " + token);
                }
                return index;
            } catch (NumberFormatException e) {
                throw new JsonException("JSON pointer token is not an array index: " + token, e);
            }
        }

        /**
         * Decodes JSON Pointer escape sequences in a path token.
         */
        private static String unescape(String token) {
            return token.replace("~1", "/").replace("~0", "~");
        }

        private sealed interface PathFrame permits ObjectFrame, ArrayFrame {
            JsonValue withUpdatedChild(JsonValue child);
        }

        record ObjectFrame(JsonObject object, String token) implements PathFrame {
            /**
             * Rebuilds this path frame with the updated child value.
             */
            @Override
            public JsonValue withUpdatedChild(JsonValue child) {
                Map<String, JsonValue> copy = new LinkedHashMap<>(object);
                copy.put(token, child);
                return new JsonObjectValue(copy);
            }
        }

        record ArrayFrame(JsonArray array, int index) implements PathFrame {
            /**
             * Rebuilds this path frame with the updated child value.
             */
            @Override
            public JsonValue withUpdatedChild(JsonValue child) {
                List<JsonValue> copy = new ArrayList<>(array);
                copy.set(index, child);
                return new JsonArrayValue(copy);
            }
        }

        private enum Operation {
            ADD,
            REPLACE,
            REMOVE
        }
    }
}
