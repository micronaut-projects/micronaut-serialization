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
package io.micronaut.serde.jsonb;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import jakarta.annotation.Priority;
import jakarta.inject.Singleton;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.adapter.JsonbAdapter;
import jakarta.json.bind.config.PropertyVisibilityStrategy;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.bind.serializer.SerializationContext;
import jakarta.json.stream.JsonGenerator;
import jakarta.json.stream.JsonParser;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonbExtensionBeanTest {
    @Test
    void jsonbExtensionBeansAreAppliedInBeanOrder() throws Exception {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "jsonb-extension-beans"))) {
            Jsonb jsonb = context.getBean(Jsonb.class);

            assertEquals("\"serializer-high:a\"", jsonb.toJson(new BeanCallbackValue("a")));
            assertEquals("deserializer-high:b", jsonb.fromJson("\"b\"", BeanCallbackValue.class).value);

            assertEquals("\"adapter-high:c\"", jsonb.toJson(new BeanAdapterValue("c")));
            assertEquals("adapter-high:d", jsonb.fromJson("\"d\"", BeanAdapterValue.class).value);
        }
    }

    @Test
    void explicitJsonbConfigRegistrationKeepsConfiguredOrder() throws Exception {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "jsonb-config-registration"))) {
            Jsonb jsonb = context.getBean(Jsonb.class);

            assertEquals("\"configured-first:a\"", jsonb.toJson(new ConfiguredCallbackValue("a")));
            assertEquals("configured-first:b", jsonb.fromJson("\"b\"", ConfiguredCallbackValue.class).value);

            assertEquals("\"configured-adapter-first:c\"", jsonb.toJson(new ConfiguredAdapterValue("c")));
            assertEquals("configured-adapter-first:d", jsonb.fromJson("\"d\"", ConfiguredAdapterValue.class).value);
        }
    }

    @Test
    void propertyVisibilityStrategyBeanIsApplied() throws Exception {
        try (ApplicationContext context = ApplicationContext.run(Map.of("spec.name", "jsonb-visibility-bean"))) {
            Jsonb jsonb = context.getBean(Jsonb.class);

            assertEquals("{\"value\":\"a\"}", jsonb.toJson(new VisibilityValue("a")));
            assertEquals("b", jsonb.fromJson("{\"value\":\"b\"}", VisibilityValue.class).value);
        }
    }

    private static String readString(JsonParser parser) {
        while (parser.hasNext()) {
            if (parser.next() == JsonParser.Event.VALUE_STRING) {
                return parser.getString();
            }
        }
        throw new IllegalStateException("Expected JSON string");
    }

    static final class BeanCallbackValue {
        final String value;

        BeanCallbackValue(String value) {
            this.value = value;
        }
    }

    static final class BeanAdapterValue {
        final String value;

        BeanAdapterValue(String value) {
            this.value = value;
        }
    }

    static final class ConfiguredCallbackValue {
        final String value;

        ConfiguredCallbackValue(String value) {
            this.value = value;
        }
    }

    static final class ConfiguredAdapterValue {
        final String value;

        ConfiguredAdapterValue(String value) {
            this.value = value;
        }
    }

    static final class VisibilityValue {
        private String value;

        public VisibilityValue() {
        }

        VisibilityValue(String value) {
            this.value = value;
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "jsonb-extension-beans")
    @Priority(20)
    static final class LowPriorityBeanSerializer implements JsonbSerializer<BeanCallbackValue> {
        @Override
        public void serialize(BeanCallbackValue obj, JsonGenerator generator, SerializationContext ctx) {
            generator.write("serializer-low:" + obj.value);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "jsonb-extension-beans")
    @Priority(10)
    static final class HighPriorityBeanSerializer implements JsonbSerializer<BeanCallbackValue> {
        @Override
        public void serialize(BeanCallbackValue obj, JsonGenerator generator, SerializationContext ctx) {
            generator.write("serializer-high:" + obj.value);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "jsonb-extension-beans")
    @Priority(20)
    static final class LowPriorityBeanDeserializer implements JsonbDeserializer<BeanCallbackValue> {
        @Override
        public BeanCallbackValue deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
            return new BeanCallbackValue("deserializer-low:" + readString(parser));
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "jsonb-extension-beans")
    @Priority(10)
    static final class HighPriorityBeanDeserializer implements JsonbDeserializer<BeanCallbackValue> {
        @Override
        public BeanCallbackValue deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
            return new BeanCallbackValue("deserializer-high:" + readString(parser));
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "jsonb-extension-beans")
    @Priority(20)
    static final class LowPriorityBeanAdapter implements JsonbAdapter<BeanAdapterValue, String> {
        @Override
        public String adaptToJson(BeanAdapterValue obj) {
            return "adapter-low:" + obj.value;
        }

        @Override
        public BeanAdapterValue adaptFromJson(String obj) {
            return new BeanAdapterValue("adapter-low:" + obj);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "jsonb-extension-beans")
    @Priority(10)
    static final class HighPriorityBeanAdapter implements JsonbAdapter<BeanAdapterValue, String> {
        @Override
        public String adaptToJson(BeanAdapterValue obj) {
            return "adapter-high:" + obj.value;
        }

        @Override
        public BeanAdapterValue adaptFromJson(String obj) {
            return new BeanAdapterValue("adapter-high:" + obj);
        }
    }

    @Factory
    @Requires(property = "spec.name", value = "jsonb-config-registration")
    static final class ProgrammaticJsonbConfigFactory {
        @Singleton
        JsonbConfig jsonbConfig() {
            return new JsonbConfig()
                .withSerializers(new FirstConfiguredSerializer(), new SecondConfiguredSerializer())
                .withDeserializers(new FirstConfiguredDeserializer(), new SecondConfiguredDeserializer())
                .withAdapters(new FirstConfiguredAdapter(), new SecondConfiguredAdapter());
        }
    }

    static final class FirstConfiguredSerializer implements JsonbSerializer<ConfiguredCallbackValue> {
        @Override
        public void serialize(ConfiguredCallbackValue obj, JsonGenerator generator, SerializationContext ctx) {
            generator.write("configured-first:" + obj.value);
        }
    }

    static final class SecondConfiguredSerializer implements JsonbSerializer<ConfiguredCallbackValue> {
        @Override
        public void serialize(ConfiguredCallbackValue obj, JsonGenerator generator, SerializationContext ctx) {
            generator.write("configured-second:" + obj.value);
        }
    }

    static final class FirstConfiguredDeserializer implements JsonbDeserializer<ConfiguredCallbackValue> {
        @Override
        public ConfiguredCallbackValue deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
            return new ConfiguredCallbackValue("configured-first:" + readString(parser));
        }
    }

    static final class SecondConfiguredDeserializer implements JsonbDeserializer<ConfiguredCallbackValue> {
        @Override
        public ConfiguredCallbackValue deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
            return new ConfiguredCallbackValue("configured-second:" + readString(parser));
        }
    }

    static final class FirstConfiguredAdapter implements JsonbAdapter<ConfiguredAdapterValue, String> {
        @Override
        public String adaptToJson(ConfiguredAdapterValue obj) {
            return "configured-adapter-first:" + obj.value;
        }

        @Override
        public ConfiguredAdapterValue adaptFromJson(String obj) {
            return new ConfiguredAdapterValue("configured-adapter-first:" + obj);
        }
    }

    static final class SecondConfiguredAdapter implements JsonbAdapter<ConfiguredAdapterValue, String> {
        @Override
        public String adaptToJson(ConfiguredAdapterValue obj) {
            return "configured-adapter-second:" + obj.value;
        }

        @Override
        public ConfiguredAdapterValue adaptFromJson(String obj) {
            return new ConfiguredAdapterValue("configured-adapter-second:" + obj);
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "jsonb-visibility-bean")
    static final class FieldVisibilityStrategy implements PropertyVisibilityStrategy {
        @Override
        public boolean isVisible(Field field) {
            return "value".equals(field.getName());
        }

        @Override
        public boolean isVisible(Method method) {
            return false;
        }
    }
}
