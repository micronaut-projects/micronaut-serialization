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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.clhm.ConcurrentLinkedHashMap;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.config.CoercionPolicy;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.SerializationConfiguration;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.jackson.JacksonDecoder;
import io.micronaut.serde.jackson.JacksonEncoder;
import jakarta.json.JsonStructure;
import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;
import jakarta.json.bind.JsonbException;
import jakarta.json.bind.config.BinaryDataStrategy;
import jakarta.json.bind.config.PropertyNamingStrategy;
import jakarta.json.bind.config.PropertyOrderStrategy;
import jakarta.json.bind.spi.JsonbProvider;
import jakarta.json.spi.JsonProvider;
import org.jspecify.annotations.Nullable;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.PrettyPrinter;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.util.DefaultPrettyPrinter;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentMap;

/**
 * Micronaut Serialization backed JSON-B provider using generated serializers and deserializers.
 *
 * @since 3.1.0
 */
@Internal
public class MicronautJsonbProvider extends JsonbProvider {
    @Override
    public JsonbBuilder create() {
        return new Builder();
    }

    protected static class Builder implements JsonbBuilder {
        protected JsonbConfig config = new JsonbConfig();
        protected @Nullable JsonProvider jsonProvider;

        @Override
        public JsonbBuilder withConfig(JsonbConfig config) {
            this.config = Objects.requireNonNull(config, "config");
            return this;
        }

        @Override
        public JsonbBuilder withProvider(JsonProvider jsonProvider) {
            this.jsonProvider = Objects.requireNonNull(jsonProvider, "jsonProvider");
            return this;
        }

        /**
         * Builds a JSON-B instance from the configured properties.
         *
         * @return The JSON-B instance
         */
        @Override
        public Jsonb build() {
            return build(config, jsonProvider);
        }

        /**
         * Extension point for providers that need a specialized JSON-B implementation.
         *
         * @param config The JSON-B configuration
         * @param jsonProvider The optional JSON-P provider
         * @return The JSON-B instance
         */
        protected Jsonb build(JsonbConfig config, @Nullable JsonProvider jsonProvider) {
            return new MicronautJsonb(config, jsonProvider);
        }
    }

    @SuppressWarnings({"java:S1452", "java:S1845", "java:S2583"})
    protected static class MicronautJsonb implements Jsonb {
        private static final int GENERATED_SERDE_CACHE_MAXIMUM_SIZE = 256;
        private static final String DEFAULT_FORMAT = "##default";
        private static final String TIME_IN_MILLIS_FORMAT = "##time-in-millis";

        protected final ObjectMapper mapper;
        protected final SerdeRegistry registry;
        protected final JsonFactory jsonFactory;
        protected final Charset charset;
        protected final boolean prettyPrint;
        protected final boolean strictIJson;
        protected final String binaryDataStrategy;
        protected final @Nullable SerdeConfiguration serdeConfiguration;
        private final ConcurrentMap<Argument<?>, Boolean> generatedDeserializerAvailability = generatedSerdeCache();
        private final ConcurrentMap<Argument<?>, Boolean> generatedSerializerAvailability = generatedSerdeCache();
        private final boolean reflectionOnlyFeatures;
        private final Runnable closeAction;

        protected MicronautJsonb(JsonbConfig config, @Nullable JsonProvider jsonProvider) {
            this(config, standaloneMapper(config, jsonProvider));
        }

        private MicronautJsonb(JsonbConfig config, MapperAndClose mapperAndClose) {
            this(config, mapperAndClose.mapper(), mapperAndClose.closeAction());
        }

        protected MicronautJsonb(JsonbConfig config,
                                 ObjectMapper objectMapper,
                                 SerdeConfiguration serdeConfiguration,
                                 SerializationConfiguration serializationConfiguration,
                                 DeserializationConfiguration deserializationConfiguration) {
            this(config, objectMapper.cloneWithConfiguration(
                new JsonbSerdeConfiguration(config, serdeConfiguration),
                new JsonbSerializationConfiguration(config, serializationConfiguration),
                new JsonbDeserializationConfiguration(config, deserializationConfiguration)
            ), new JsonbSerdeConfiguration(config, serdeConfiguration), () -> {
            });
        }

        protected MicronautJsonb(JsonbConfig config, ObjectMapper mapper, Runnable closeAction) {
            this(config, mapper, mapper.getSerdeRegistry().newEncoderContext(null).getSerdeConfiguration().orElse(null), closeAction);
        }

        protected MicronautJsonb(JsonbConfig config, ObjectMapper mapper, @Nullable SerdeConfiguration serdeConfiguration, Runnable closeAction) {
            this.mapper = mapper;
            this.closeAction = closeAction;
            this.registry = mapper.getSerdeRegistry();
            this.jsonFactory = new JsonFactory();
            this.charset = charset(config);
            this.prettyPrint = config.getProperty(JsonbConfig.FORMATTING).filter(Boolean.TRUE::equals).isPresent();
            this.strictIJson = config.getProperty(JsonbConfig.STRICT_IJSON).filter(Boolean.TRUE::equals).isPresent();
            this.binaryDataStrategy = binaryDataStrategy(config);
            this.serdeConfiguration = serdeConfiguration;
            this.reflectionOnlyFeatures = hasReflectionOnlyFeatures(config);
        }

        /**
         * Creates a bounded cache for per-mapper generated Serde capability checks.
         * The cache is intentionally small because it protects hot reflection-provider
         * routing paths, not application domain data.
         *
         * @param <K> The cache key type
         * @param <V> The cache value type
         * @return A concurrent bounded cache
         */
        protected static <K, V> ConcurrentMap<K, V> generatedSerdeCache() {
            return new ConcurrentLinkedHashMap.Builder<K, V>()
                .maximumWeightedCapacity(GENERATED_SERDE_CACHE_MAXIMUM_SIZE)
                .build();
        }

        private static MapperAndClose standaloneMapper(JsonbConfig config, @Nullable JsonProvider jsonProvider) {
            Map<String, Object> properties = properties(config);
            if (jsonProvider != null) {
                properties.put("micronaut.serde.jsonb.provider", jsonProvider.getClass().getName());
            }
            ObjectMapper.CloseableObjectMapper mapper = ObjectMapper.create(properties, additionalPackages(config));
            return new MapperAndClose(mapper, mapper::close);
        }

        @Override
        public <T> @Nullable T fromJson(String str, Class<T> type) throws JsonbException {
            return readString(str, Argument.of(type));
        }

        @Override
        @SuppressWarnings("TypeParameterUnusedInFormals")
        public <T> @Nullable T fromJson(String str, java.lang.reflect.Type runtimeType) throws JsonbException {
            @SuppressWarnings("unchecked")
            T value = (T) readString(str, argument(runtimeType));
            return value;
        }

        @Override
        public <T> @Nullable T fromJson(Reader reader, Class<T> type) throws JsonbException {
            return readReader(reader, Argument.of(type));
        }

        @Override
        @SuppressWarnings("TypeParameterUnusedInFormals")
        public <T> @Nullable T fromJson(Reader reader, java.lang.reflect.Type runtimeType) throws JsonbException {
            @SuppressWarnings("unchecked")
            T value = (T) readReader(reader, argument(runtimeType));
            return value;
        }

        @Override
        public <T> @Nullable T fromJson(InputStream stream, Class<T> type) throws JsonbException {
            return readStream(stream, Argument.of(type));
        }

        @Override
        @SuppressWarnings("TypeParameterUnusedInFormals")
        public <T> @Nullable T fromJson(InputStream stream, java.lang.reflect.Type runtimeType) throws JsonbException {
            @SuppressWarnings("unchecked")
            T value = (T) readStream(stream, argument(runtimeType));
            return value;
        }

        @Override
        public String toJson(Object object) throws JsonbException {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            toJson(object, outputStream);
            return outputStream.toString(charset);
        }

        @Override
        public String toJson(Object object, java.lang.reflect.Type runtimeType) throws JsonbException {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            toJson(object, runtimeType, outputStream);
            return outputStream.toString(charset);
        }

        @Override
        public void toJson(Object object, Writer writer) throws JsonbException {
            validateStrictTopLevel(object);
            try {
                @SuppressWarnings({"unchecked", "rawtypes"})
                Argument<Object> argument = object == null ? Argument.OBJECT_ARGUMENT : (Argument) Argument.of(object.getClass());
                writeGenerated(object, argument, () -> jsonFactory.createGenerator(new JsonbWriteContext(prettyPrint), new NonClosingWriter(writer)));
            } catch (IOException | RuntimeException e) {
                throw new JsonbException("Cannot write JSON-B value", e);
            }
        }

        @Override
        public void toJson(Object object, java.lang.reflect.Type runtimeType, Writer writer) throws JsonbException {
            validateStrictTopLevel(object);
            try {
                @SuppressWarnings({"rawtypes", "unchecked"})
                Argument<Object> argument = (Argument) argument(runtimeType);
                writeGenerated(object, argument, () -> jsonFactory.createGenerator(new JsonbWriteContext(prettyPrint), new NonClosingWriter(writer)));
            } catch (IOException | RuntimeException e) {
                throw new JsonbException("Cannot write JSON-B value", e);
            }
        }

        @Override
        public void toJson(Object object, OutputStream stream) throws JsonbException {
            validateStrictTopLevel(object);
            try {
                @SuppressWarnings({"unchecked", "rawtypes"})
                Argument<Object> argument = object == null ? Argument.OBJECT_ARGUMENT : (Argument) Argument.of(object.getClass());
                writeGenerated(object, argument, () -> jsonFactory.createGenerator(new JsonbWriteContext(prettyPrint), new NonClosingOutputStream(stream)));
            } catch (IOException | RuntimeException e) {
                throw new JsonbException("Cannot write JSON-B value", e);
            }
        }

        @Override
        public void toJson(Object object, java.lang.reflect.Type runtimeType, OutputStream stream) throws JsonbException {
            validateStrictTopLevel(object);
            try {
                @SuppressWarnings({"rawtypes", "unchecked"})
                Argument<Object> argument = (Argument) argument(runtimeType);
                writeGenerated(object, argument, () -> jsonFactory.createGenerator(new JsonbWriteContext(prettyPrint), new NonClosingOutputStream(stream)));
            } catch (IOException | RuntimeException e) {
                throw new JsonbException("Cannot write JSON-B value", e);
            }
        }

        @Override
        public void close() {
            closeAction.run();
        }

        /**
         * Reads a JSON string through the generated deserializer path. Subclasses may override to provide
         * non-generated fallback behavior.
         *
         * @param str The JSON string
         * @param argument The target argument
         * @param <T> The target type
         * @return The decoded value
         */
        protected <T> @Nullable T readString(String str, Argument<T> argument) {
            return readGenerated(argument, () -> jsonFactory.createParser(ObjectReadContext.empty(), str));
        }

        /**
         * Reads JSON from a reader through the generated deserializer path. Subclasses may override to provide
         * non-generated fallback behavior while preserving streaming reads.
         *
         * @param reader The JSON reader
         * @param argument The target argument
         * @param <T> The target type
         * @return The decoded value
         */
        protected <T> @Nullable T readReader(Reader reader, Argument<T> argument) {
            return readGenerated(argument, () -> jsonFactory.createParser(ObjectReadContext.empty(), reader));
        }

        /**
         * Reads JSON from a stream through the generated deserializer path. Subclasses may override to provide
         * non-generated fallback behavior while preserving streaming reads.
         *
         * @param stream The JSON input stream
         * @param argument The target argument
         * @param <T> The target type
         * @return The decoded value
         */
        protected <T> @Nullable T readStream(InputStream stream, Argument<T> argument) {
            return readGenerated(argument, () -> jsonFactory.createParser(ObjectReadContext.empty(), stream));
        }

        /**
         * Reads JSON from a parser source with the generated deserializer for the supplied argument.
         *
         * @param argument The target argument
         * @param parserSource The parser source
         * @param <T> The target type
         * @return The decoded value
         */
        protected <T> @Nullable T readGenerated(Argument<T> argument, ParserSource parserSource) {
            ensureGeneratedOnlyFeatures();
            try {
                Deserializer.DecoderContext decoderContext = registry.newDecoderContext(null);
                Deserializer<? extends T> deserializer = decoderContext.findDeserializer(argument).createSpecific(decoderContext, argument);
                try (JsonParser parser = parserSource.createParser()) {
                    return deserializer.deserializeNullable(new JsonbDecoder(JacksonDecoder.create(parser, limits(), coercionPolicy(decoderContext)), binaryDataStrategy), decoderContext, argument);
                }
            } catch (IOException | RuntimeException e) {
                throw new JsonbException("Cannot read JSON-B value", e);
            }
        }

        /**
         * Checks whether a generated deserializer can be created for the argument.
         * <p>
         * Registry lookup is intentionally cached because negative checks can be
         * exception-heavy when JSON-B reflection fallback probes whether a value
         * may use the generated path directly.
         *
         * @param argument The target argument
         * @param <T> The target type
         * @return Whether the deserializer can be created
         */
        protected <T> boolean canCreateGeneratedDeserializer(Argument<T> argument) {
            return generatedDeserializerAvailability.computeIfAbsent(argument, this::canCreateGeneratedDeserializerUncached);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private boolean canCreateGeneratedDeserializerUncached(Argument<?> argument) {
            try {
                Argument<Object> typedArgument = (Argument) argument;
                Deserializer.DecoderContext decoderContext = registry.newDecoderContext(null);
                decoderContext.findDeserializer(typedArgument).createSpecific(decoderContext, typedArgument);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        /**
         * Checks whether a generated serializer can be created for the argument.
         * <p>
         * Registry lookup is intentionally cached because negative checks can be
         * exception-heavy when JSON-B reflection fallback probes whether a value
         * may use the generated path directly.
         *
         * @param argument The target argument
         * @param <T> The target type
         * @return Whether the serializer can be created
         */
        protected <T> boolean canCreateGeneratedSerializer(Argument<T> argument) {
            return generatedSerializerAvailability.computeIfAbsent(argument, this::canCreateGeneratedSerializerUncached);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private boolean canCreateGeneratedSerializerUncached(Argument<?> argument) {
            try {
                Argument<Object> typedArgument = (Argument) argument;
                Serializer.EncoderContext encoderContext = registry.newEncoderContext(null);
                encoderContext.findSerializer(typedArgument).createSpecific(encoderContext, typedArgument);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        /**
         * Writes JSON through the generated serializer path.
         *
         * @param object The value to write
         * @param argument The value argument
         * @param generatorSource The generator source
         * @param <T> The value type
         * @throws IOException If JSON writing fails
         */
        protected <T> void writeGenerated(@Nullable T object, Argument<T> argument, GeneratorSource generatorSource) throws IOException {
            ensureGeneratedOnlyFeatures();
            try (JsonGenerator generator = generatorSource.createGenerator()) {
                if (object == null) {
                    generator.writeNull();
                } else {
                    Serializer.EncoderContext encoderContext = registry.newEncoderContext(null);
                    Serializer<? super T> serializer = encoderContext.findSerializer(argument).createSpecific(encoderContext, argument);
                    serializer.serialize(new JsonbEncoder(JacksonEncoder.create(generator, limits()), binaryDataStrategy), encoderContext, argument, object);
                }
            }
        }

        protected final CoercionPolicy coercionPolicy(Deserializer.DecoderContext decoderContext) {
            return decoderContext.getDeserializationConfiguration()
                .map(CoercionPolicy::fromConfiguration)
                .orElse(CoercionPolicy.LENIENT);
        }

        protected final LimitingStream.RemainingLimits limits() {
            return serdeConfiguration == null ? LimitingStream.DEFAULT_LIMITS : LimitingStream.limitsFromConfiguration(serdeConfiguration);
        }

        /**
         * Validates strict I-JSON top-level constraints before writing.
         *
         * @param object The value to write
         */
        protected void validateStrictTopLevel(@Nullable Object object) {
            if (!strictIJson || object instanceof JsonStructure) {
                return;
            }
            if (object == null || isJsonScalar(object.getClass())) {
                throw new JsonbException("Strict I-JSON requires a top-level object or array");
            }
        }

        /**
         * Ensures reflection-only JSON-B features are not used by the generated provider.
         */
        protected void ensureGeneratedOnlyFeatures() {
            if (reflectionOnlyFeatures) {
                throw new JsonbException("This JSON-B configuration requires MicronautJsonbReflectionProvider");
            }
        }

        static boolean hasReflectionOnlyFeatures(JsonbConfig config) {
            return config.getProperty(JsonbConfig.PROPERTY_VISIBILITY_STRATEGY).isPresent()
                || config.getProperty(JsonbConfig.ADAPTERS).isPresent()
                || config.getProperty(JsonbConfig.SERIALIZERS).isPresent()
                || config.getProperty(JsonbConfig.DESERIALIZERS).isPresent()
                || PropertyOrderStrategy.REVERSE.equals(propertyOrderStrategy(config));
        }

        protected static Argument<?> argument(java.lang.reflect.Type runtimeType) {
            return JsonbBridgeSupport.argument(runtimeType);
        }

        protected static Map<String, Object> properties(JsonbConfig config) {
            Map<String, Object> properties = new LinkedHashMap<>();
            config.getProperty(JsonbConfig.FORMATTING)
                .filter(Boolean.TRUE::equals)
                .ifPresent(ignored -> properties.put("micronaut.serde.jackson.pretty-print", true));
            config.getProperty(JsonbConfig.NULL_VALUES)
                .ifPresent(value -> properties.put("micronaut.serde.serialization.inclusion", Boolean.TRUE.equals(value) ? "ALWAYS" : "NON_NULL"));
            if (BinaryDataStrategy.BASE_64.equals(binaryDataStrategy(config)) || BinaryDataStrategy.BASE_64_URL.equals(binaryDataStrategy(config))) {
                properties.put("micronaut.serde.write-binary-as-array", false);
            }
            properties.put("micronaut.serde.write-durations-as-strings", true);
            properties.put("micronaut.serde.write-java-util-dates-with-zone-id", true);
            properties.put("micronaut.serde.reject-deprecated-three-letter-time-zone-ids", true);
            if (config.getProperty(JsonbConfig.STRICT_IJSON).filter(Boolean.TRUE::equals).isPresent()) {
                properties.put("micronaut.serde.write-date-times-as-strict-ijson", true);
            }
            config.getProperty(JsonbConfig.DATE_FORMAT)
                .map(String::valueOf)
                .filter(format -> !DEFAULT_FORMAT.equals(format) && !TIME_IN_MILLIS_FORMAT.equals(format))
                .ifPresent(format -> {
                    properties.put("micronaut.serde.date-format", format);
                    properties.put("micronaut.serde.time-zone", "UTC");
                });
            config.getProperty(JsonbConfig.LOCALE)
                .ifPresent(locale -> properties.put("micronaut.serde.locale", locale));
            if (PropertyOrderStrategy.LEXICOGRAPHICAL.equals(propertyOrderStrategy(config))) {
                properties.put("micronaut.serde.serialization.sort-properties-alphabetically", true);
            }
            mappedPropertyNamingStrategy(config)
                .ifPresent(strategy -> properties.put("micronaut.serde.property-naming-strategy", strategy));
            config.getProperty(JsonbConfig.CREATOR_PARAMETERS_REQUIRED)
                .filter(Boolean.TRUE::equals)
                .ifPresent(ignored -> properties.put("micronaut.serde.deserialization.require-all-creator-parameters", true));
            config.getProperty("jsonb.fail-on-unknown-properties")
                .filter(Boolean.TRUE::equals)
                .ifPresent(ignored -> properties.put("micronaut.serde.deserialization.ignore-unknown", false));
            config.getProperty("micronaut.serde.maximum-nesting-depth")
                .ifPresent(value -> properties.put("micronaut.serde.maximum-nesting-depth", value));
            return properties;
        }

        protected static String[] additionalPackages(JsonbConfig config) {
            return config.getProperty(JsonbConfiguration.ADDITIONAL_PACKAGES)
                .map(MicronautJsonb::additionalPackages)
                .orElseGet(() -> new String[0]);
        }

        private static String[] additionalPackages(Object value) {
            List<String> packageNames = new ArrayList<>();
            switch (value) {
                case String string -> addCommaDelimitedPackageNames(packageNames, string);
                case String[] strings -> {
                    for (String packageName : strings) {
                        addPackageName(packageNames, packageName);
                    }
                }
                case Collection<?> collection -> {
                    for (Object item : collection) {
                        addPackageName(packageNames, String.valueOf(item));
                    }
                }
                default -> addPackageName(packageNames, String.valueOf(value));
            }
            return packageNames.toArray(String[]::new);
        }

        private static void addCommaDelimitedPackageNames(List<String> packageNames, String value) {
            int start = 0;
            while (start <= value.length()) {
                int comma = value.indexOf(',', start);
                if (comma < 0) {
                    addPackageName(packageNames, value.substring(start));
                    return;
                }
                addPackageName(packageNames, value.substring(start, comma));
                start = comma + 1;
            }
        }

        private static void addPackageName(List<String> packageNames, String packageName) {
            String trimmed = packageName.trim();
            if (!trimmed.isEmpty()) {
                packageNames.add(trimmed);
            }
        }

        protected static Optional<String> mappedPropertyNamingStrategy(JsonbConfig config) {
            return config.getProperty(JsonbConfig.PROPERTY_NAMING_STRATEGY)
                .map(String::valueOf)
                .flatMap(strategy -> switch (strategy) {
                    case PropertyNamingStrategy.LOWER_CASE_WITH_DASHES -> Optional.of("KEBAB_CASE");
                    case PropertyNamingStrategy.LOWER_CASE_WITH_UNDERSCORES -> Optional.of("SNAKE_CASE");
                    case PropertyNamingStrategy.UPPER_CAMEL_CASE -> Optional.of("UPPER_CAMEL_CASE");
                    case PropertyNamingStrategy.UPPER_CAMEL_CASE_WITH_SPACES -> Optional.of("UPPER_CAMEL_CASE_WITH_SPACES");
                    case PropertyNamingStrategy.IDENTITY, PropertyNamingStrategy.CASE_INSENSITIVE -> Optional.empty();
                    default -> Optional.empty();
                });
        }

        protected static String propertyOrderStrategy(JsonbConfig config) {
            return config.getProperty(JsonbConfig.PROPERTY_ORDER_STRATEGY)
                .map(String::valueOf)
                .orElse(PropertyOrderStrategy.LEXICOGRAPHICAL);
        }

        protected static String binaryDataStrategy(JsonbConfig config) {
            if (config.getProperty(JsonbConfig.STRICT_IJSON).filter(Boolean.TRUE::equals).isPresent()) {
                return BinaryDataStrategy.BASE_64_URL;
            }
            return config.getProperty(JsonbConfig.BINARY_DATA_STRATEGY)
                .map(String::valueOf)
                .orElse(BinaryDataStrategy.BYTE);
        }

        protected static Charset charset(JsonbConfig config) {
            Optional<Object> property = config.getProperty(JsonbConfig.ENCODING);
            return property.map(o -> Charset.forName(String.valueOf(o))).orElse(StandardCharsets.UTF_8);
        }

        protected static boolean isJsonScalar(Class<?> type) {
            return JsonbScalarTypes.isJsonScalar(type);
        }

        protected record MapperAndClose(ObjectMapper mapper, Runnable closeAction) {
        }
    }

    protected record JsonbSerdeConfiguration(JsonbConfig jsonbConfig,
                                             SerdeConfiguration delegate) implements SerdeConfiguration {
        @Override
        public Optional<String> getDateFormat() {
            return jsonbConfig.getProperty(JsonbConfig.DATE_FORMAT)
                .map(String::valueOf)
                .filter(format -> !MicronautJsonb.DEFAULT_FORMAT.equals(format) && !MicronautJsonb.TIME_IN_MILLIS_FORMAT.equals(format))
                .or(delegate::getDateFormat);
        }

        @Override
        public TimeShape getTimeWriteShape() {
            return delegate.getTimeWriteShape();
        }

        @Override
        public NumericTimeUnit getNumericTimeUnit() {
            return delegate.getNumericTimeUnit();
        }

        @Override
        public boolean isWriteBinaryAsArray() {
            String configuredBinaryDataStrategy = MicronautJsonb.binaryDataStrategy(jsonbConfig);
            if (BinaryDataStrategy.BASE_64.equals(configuredBinaryDataStrategy) || BinaryDataStrategy.BASE_64_URL.equals(configuredBinaryDataStrategy)) {
                return false;
            }
            return delegate.isWriteBinaryAsArray();
        }

        @Override
        public boolean isWriteDurationsAsStrings() {
            return true;
        }

        @Override
        public boolean isWriteJavaUtilDatesWithZoneId() {
            return true;
        }

        @Override
        public boolean isRejectDeprecatedThreeLetterTimeZoneIds() {
            return true;
        }

        @Override
        public boolean isWriteDateTimesAsStrictIJson() {
            return jsonbConfig.getProperty(JsonbConfig.STRICT_IJSON).filter(Boolean.TRUE::equals).isPresent()
                || delegate.isWriteDateTimesAsStrictIJson();
        }

        @Override
        public Optional<Locale> getLocale() {
            return jsonbConfig.getProperty(JsonbConfig.LOCALE)
                .map(Locale.class::cast)
                .or(delegate::getLocale);
        }

        @Override
        public Optional<TimeZone> getTimeZone() {
            if (jsonbConfig.getProperty(JsonbConfig.DATE_FORMAT)
                .map(String::valueOf)
                .filter(format -> !MicronautJsonb.DEFAULT_FORMAT.equals(format) && !MicronautJsonb.TIME_IN_MILLIS_FORMAT.equals(format))
                .isPresent()) {
                return Optional.of(TimeZone.getTimeZone("UTC"));
            }
            return delegate.getTimeZone();
        }

        @Override
        public List<String> getIncludedIntrospectionPackages() {
            return delegate.getIncludedIntrospectionPackages();
        }

        @Override
        public int getMaximumNestingDepth() {
            return delegate.getMaximumNestingDepth();
        }

        @Override
        public boolean isInetAddressAsNumeric() {
            return delegate.isInetAddressAsNumeric();
        }

        @Override
        public @Nullable String getPropertyNamingStrategyName() {
            return MicronautJsonb.mappedPropertyNamingStrategy(jsonbConfig)
                .orElseGet(delegate::getPropertyNamingStrategyName);
        }

        @Override
        public boolean isJsonViewEnabled() {
            return delegate.isJsonViewEnabled();
        }
    }

    record JsonbSerializationConfiguration(JsonbConfig jsonbConfig,
                                           SerializationConfiguration delegate,
                                           boolean forceDisableGeneratedSerializer) implements SerializationConfiguration {
        JsonbSerializationConfiguration(JsonbConfig jsonbConfig,
                                        SerializationConfiguration delegate) {
            this(jsonbConfig, delegate, false);
        }

        @Override
        public SerdeConfig.SerInclude getInclusion() {
            return jsonbConfig.getProperty(JsonbConfig.NULL_VALUES)
                .map(value -> Boolean.TRUE.equals(value) ? SerdeConfig.SerInclude.ALWAYS : SerdeConfig.SerInclude.NON_NULL)
                .orElseGet(delegate::getInclusion);
        }

        @Override
        public boolean isAlwaysSerializeErrorsAsList() {
            return delegate.isAlwaysSerializeErrorsAsList();
        }

        @Override
        public boolean sortPropertiesAlphabetically() {
            String propertyOrderStrategy = MicronautJsonb.propertyOrderStrategy(jsonbConfig);
            if (PropertyOrderStrategy.LEXICOGRAPHICAL.equals(propertyOrderStrategy)) {
                return true;
            }
            if (PropertyOrderStrategy.REVERSE.equals(propertyOrderStrategy)) {
                return false;
            }
            return delegate.sortPropertiesAlphabetically();
        }

        @Override
        public boolean writeDateTimestampsAsNanoseconds() {
            return delegate.writeDateTimestampsAsNanoseconds();
        }

        @Override
        public boolean writeDatesWithZoneId() {
            return delegate.writeDatesWithZoneId();
        }

        @Override
        public boolean writeSingleElemArraysUnwrapped() {
            return delegate.writeSingleElemArraysUnwrapped();
        }

        @Override
        public boolean writeSortedMapEntries() {
            return delegate.writeSortedMapEntries();
        }

        @Override
        public boolean disableGeneratedSerializer() {
            return forceDisableGeneratedSerializer || delegate.disableGeneratedSerializer();
        }
    }

    record JsonbDeserializationConfiguration(JsonbConfig jsonbConfig,
                                             DeserializationConfiguration delegate,
                                             boolean forceDisableGeneratedDeserializer) implements DeserializationConfiguration {
        JsonbDeserializationConfiguration(JsonbConfig jsonbConfig,
                                          DeserializationConfiguration delegate) {
            this(jsonbConfig, delegate, false);
        }

        @Override
        public boolean isIgnoreUnknown() {
            return jsonbConfig.getProperty("jsonb.fail-on-unknown-properties").filter(Boolean.TRUE::equals).isEmpty()
                && delegate.isIgnoreUnknown();
        }

        @Override
        public int getArraySizeThreshold() {
            return delegate.getArraySizeThreshold();
        }

        @Override
        public boolean isStrictNullable() {
            return delegate.isStrictNullable();
        }

        @Override
        public boolean isFailOnNullForPrimitives() {
            return delegate.isFailOnNullForPrimitives();
        }

        @Override
        public boolean isSubtypesRequireDefaultImpl() {
            return delegate.isSubtypesRequireDefaultImpl();
        }

        @Override
        public boolean acceptCaseInsensitiveEnums() {
            return delegate.acceptCaseInsensitiveEnums();
        }

        @Override
        public boolean acceptSingleValueAsArray() {
            return delegate.acceptSingleValueAsArray();
        }

        @Override
        public boolean acceptCaseInsensitiveProperties() {
            return delegate.acceptCaseInsensitiveProperties();
        }

        @Override
        public boolean readUnknownEnumValuesAsNull() {
            return delegate.readUnknownEnumValuesAsNull();
        }

        @Override
        public boolean readUnknownEnumValuesUsingDefaultValue() {
            return delegate.readUnknownEnumValuesUsingDefaultValue();
        }

        @Override
        public boolean readDateTimestampsAsNanoseconds() {
            return delegate.readDateTimestampsAsNanoseconds();
        }

        @Override
        public boolean adjustDatesToContextTimeZone() {
            return delegate.adjustDatesToContextTimeZone();
        }

        @Override
        public boolean isRequireAllCreatorParameters() {
            return jsonbConfig.getProperty(JsonbConfig.CREATOR_PARAMETERS_REQUIRED).filter(Boolean.TRUE::equals).isPresent()
                || delegate.isRequireAllCreatorParameters();
        }

        @Override
        public boolean disableGeneratedDeserializer() {
            return forceDisableGeneratedDeserializer || delegate.disableGeneratedDeserializer();
        }
    }

    protected static final class JsonbWriteContext extends ObjectWriteContext.Base {
        private final boolean prettyPrint;

        JsonbWriteContext(boolean prettyPrint) {
            this.prettyPrint = prettyPrint;
        }

        @Override
        public @Nullable PrettyPrinter getPrettyPrinter() {
            return prettyPrint ? new DefaultPrettyPrinter() : null;
        }
    }

    @FunctionalInterface
    protected interface ParserSource {
        JsonParser createParser() throws IOException;
    }

    @FunctionalInterface
    protected interface GeneratorSource {
        JsonGenerator createGenerator() throws IOException;
    }

    protected static final class NonClosingOutputStream extends OutputStream {
        private final OutputStream delegate;

        NonClosingOutputStream(OutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.flush();
        }
    }

    protected static final class NonClosingWriter extends Writer {
        private final Writer delegate;

        NonClosingWriter(Writer delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            delegate.write(cbuf, off, len);
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.flush();
        }
    }
}
