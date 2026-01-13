/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.serde;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.ApplicationContextBuilder;
import io.micronaut.context.env.PropertySource;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.json.JsonStreamConfig;
import io.micronaut.json.tree.JsonNode;
import org.reactivestreams.Processor;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 *
 * @author graemerocher
 * @since 1.2.2
 */
@SuppressWarnings("java:S3077")
final class ObjectMappers {
    private static volatile ObjectMapper defaultObjectMapper;
    private static volatile ApplicationContext beanContext;
    private static final Object MAPPER_LOCK = new Object();
    private static final Object CONTEXT_LOCK = new Object();
    private static final Set<String> DEFAULT_INCLUDE_PACKAGES = Set.of("io.micronaut.serde",
        "io.micronaut.aop",
        "io.micronaut.runtime.context.env"
    );

    private ObjectMappers() {
    }

    /**
     * Resolves the default.
     * @return The object mapper
     */
    @SuppressWarnings("java:S2095")
    static ObjectMapper resolveDefault() {
        ObjectMapper objectMapper = defaultObjectMapper;
        if (objectMapper == null) {
            synchronized (MAPPER_LOCK) {
                objectMapper = defaultObjectMapper;
                if (objectMapper == null) {
                    objectMapper = resolveBeanContext().getBean(ObjectMapper.class);
                    defaultObjectMapper = objectMapper;
                }
            }
        }
        return objectMapper;
    }

    @SuppressWarnings("java:S2095")
    private static ApplicationContext resolveBeanContext() {
        ApplicationContext context = beanContext;
        if (context == null) {
            synchronized (CONTEXT_LOCK) {
                context = beanContext;
                if (context == null) {
                    context = createSerdeBeanContext(Map.of(), DEFAULT_INCLUDE_PACKAGES).start();
                    beanContext = context;
                }
            }
        }
        return context;
    }

    @SuppressWarnings("java:S2095")
    static ObjectMapper.CloseableObjectMapper create(Map<String, Object> configuration, String... packageNames) {
        ApplicationContext context = createSerdeBeanContext(configuration, CollectionUtils.concat(DEFAULT_INCLUDE_PACKAGES, Set.of(packageNames)));
        context.start();
        ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
        return new ObjectMapper.CloseableObjectMapper() {

            @Override
            public SerdeRegistry getSerdeRegistry() {
                return objectMapper.getSerdeRegistry();
            }

            @Override
            public <T> T readValueFromTree(JsonNode tree, Argument<T> type) throws IOException {
                return objectMapper.readValueFromTree(tree, type);
            }

            @Override
            public <T> T readValue(InputStream inputStream, Argument<T> type) throws IOException {
                return objectMapper.readValue(inputStream, type);
            }

            @Override
            public <T> T readValue(byte[] byteArray, Argument<T> type) throws IOException {
                return objectMapper.readValue(byteArray, type);
            }

            @Override
            public Processor<byte[], JsonNode> createReactiveParser(Consumer<Processor<byte[], JsonNode>> onSubscribe, boolean streamArray) {
                return objectMapper.createReactiveParser(onSubscribe, streamArray);
            }

            @Override
            public JsonNode writeValueToTree(Object value) throws IOException {
                return objectMapper.writeValueToTree(value);
            }

            @Override
            public <T> JsonNode writeValueToTree(Argument<T> type, T value) throws IOException {
                return writeValueToTree(type, value);
            }

            @Override
            public void writeValue(OutputStream outputStream, Object object) throws IOException {
                objectMapper.writeValue(outputStream, object);
            }

            @Override
            public <T> void writeValue(OutputStream outputStream, Argument<T> type, T object) throws IOException {
                objectMapper.writeValue(outputStream, type, object);
            }

            @Override
            public byte[] writeValueAsBytes(Object object) throws IOException {
                return objectMapper.writeValueAsBytes(object);
            }

            @Override
            public <T> byte[] writeValueAsBytes(Argument<T> type, T object) throws IOException {
                return objectMapper.writeValueAsBytes(type, object);
            }

            @Override
            public JsonStreamConfig getStreamConfig() {
                return objectMapper.getStreamConfig();
            }

            @Override
            public void close() {
                context.close();
            }
        };

    }

    private static ApplicationContext createSerdeBeanContext(Map<String, Object> config, Set<String> includedPackages) {
        ApplicationContextBuilder applicationContextBuilder = ApplicationContext.builder()
            .beansPredicate(qualifiedBeanType -> includedPackages.stream().anyMatch(qualifiedBeanType.getBeanType().getName()::startsWith))
            .beanConfigurationsPredicate(beanConfiguration -> includedPackages.stream().anyMatch(beanConfiguration.getPackage().getName()::startsWith))
            .eventsEnabled(false)
            .eagerBeansEnabled(false)
            .deducePackage(false)
            .bootstrapEnvironment(false)
            .deduceCloudEnvironment(false)
            .enableDefaultPropertySources(false);
        if (!config.isEmpty()) {
            applicationContextBuilder.propertySources(PropertySource.of("config", config, PropertySource.PropertyConvention.JAVA_PROPERTIES, PropertySource.Origin.of("config")));
        }
        return applicationContextBuilder
            .propertySources()
            .build();
    }
}
