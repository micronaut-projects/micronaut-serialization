/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.serde.support.serdes;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.FormatConfiguration;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.DeserializationConfiguration;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.util.SerdeFeatures;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;
import java.util.Set;

import static io.micronaut.serde.config.SerdeConfiguration.NumericTimeUnit.MILLISECONDS;
import static io.micronaut.serde.config.SerdeConfiguration.NumericTimeUnit.SECONDS;
import static io.micronaut.serde.config.SerdeConfiguration.TimeShape.DECIMAL;
import static io.micronaut.serde.config.SerdeConfiguration.TimeShape.INTEGER;
import static io.micronaut.serde.config.SerdeConfiguration.TimeShape.STRING;

/**
 * Super class that can be used for the default date/time formatting.
 *
 * @param <T> The temporal type
 * @author gkrocher
 */
public abstract sealed class DefaultFormattedTemporalSerde<T extends TemporalAccessor> implements TemporalSerde<T> permits LocalDateSerde, LocalDateTimeSerde, NumericSupportTemporalSerde {

    private final DateTimeFormatter stringFormatter;

    /**
     * @param stringFormatter The resolved string formatter
     */
    protected DefaultFormattedTemporalSerde(DateTimeFormatter stringFormatter) {
        this.stringFormatter = stringFormatter;
    }

    protected static Optional<DateTimeFormatter> createFormatter(SerdeConfiguration configuration) {
        // Creates a pattern-based formatter if there is a date format configured
        return configuration.getDateFormat()
            .map(pattern -> configuration.getLocale()
                .map(locale -> DateTimeFormatter.ofPattern(pattern, locale))
                .orElseGet(() -> DateTimeFormatter.ofPattern(pattern)))
            .map(formatter -> configuration.getTimeZone()
                .map(tz -> formatter.withZone(tz.toZoneId()))
                .orElse(formatter));
    }

    private static SerdeConfiguration.TimeShape serializationTimeShape(SerdeConfiguration configuration, FormatConfiguration.Shape shape) {
        return switch (shape) {
            case STRING -> STRING;
            case NUMBER, NUMBER_FLOAT, ARRAY -> DECIMAL;
            case NUMBER_INT -> INTEGER;
            default -> configuration.getTimeWriteShape();
        };
    }

    private static SerdeConfiguration.NumericTimeUnit serializationNumericUnit(FormatConfiguration format,
                                                                               Set<SerdeConfiguration.Feature> features) {
        if (format.shape() == FormatConfiguration.Shape.NUMBER_INT) {
            return MILLISECONDS;
        }
        if (features.contains(SerdeConfiguration.Feature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)) {
            return SECONDS;
        }
        return MILLISECONDS;
    }

    private static SerdeConfiguration.TimeShape deserializationTimeShape(SerdeConfiguration configuration, FormatConfiguration.Shape shape) {
        return switch (shape) {
            case STRING -> STRING;
            case NUMBER, NUMBER_FLOAT, ARRAY -> DECIMAL;
            case NUMBER_INT -> INTEGER;
            default -> configuration.getTimeWriteShape();
        };
    }

    private static SerdeConfiguration.NumericTimeUnit deserializationNumericUnit(FormatConfiguration format,
                                                                                 Set<DeserializationConfiguration.Feature> features) {
        if (features.contains(DeserializationConfiguration.Feature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)) {
            return SECONDS;
        }
        return MILLISECONDS;
    }

    private static SerdeConfiguration.NumericTimeUnit serializationNumericUnit(SerdeConfiguration.NumericTimeUnit numericUnit,
                                                                               Set<SerdeConfiguration.Feature> features) {
        if (features.contains(SerdeConfiguration.Feature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)) {
            return numericUnit;
        }
        return MILLISECONDS;
    }

    private static SerdeConfiguration.NumericTimeUnit serializationNumericUnit(SerdeConfiguration.NumericTimeUnit numericUnit,
                                                                               Set<SerdeConfiguration.Feature> features,
                                                                               Set<SerdeConfiguration.Feature> featuresWith,
                                                                               Set<SerdeConfiguration.Feature> featuresWithout) {
        if (featuresWith.contains(SerdeConfiguration.Feature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)) {
            return SECONDS;
        }
        if (featuresWithout.contains(SerdeConfiguration.Feature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)) {
            return MILLISECONDS;
        }
        return serializationNumericUnit(numericUnit, features);
    }

    private static SerdeConfiguration.NumericTimeUnit deserializationNumericUnit(SerdeConfiguration.NumericTimeUnit numericUnit,
                                                                                 Set<DeserializationConfiguration.Feature> features) {
        if (features.contains(DeserializationConfiguration.Feature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)) {
            return numericUnit;
        }
        return MILLISECONDS;
    }

    private static SerdeConfiguration.NumericTimeUnit deserializationNumericUnit(SerdeConfiguration.NumericTimeUnit numericUnit,
                                                                                 Set<DeserializationConfiguration.Feature> features,
                                                                                 Set<DeserializationConfiguration.Feature> featuresWith,
                                                                                 Set<DeserializationConfiguration.Feature> featuresWithout) {
        if (featuresWith.contains(DeserializationConfiguration.Feature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)) {
            return SECONDS;
        }
        if (featuresWithout.contains(DeserializationConfiguration.Feature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)) {
            return MILLISECONDS;
        }
        return deserializationNumericUnit(numericUnit, features);
    }

    @Override
    public Serializer<T> createSpecific(EncoderContext context, Argument<? extends T> type) {
        Set<SerdeConfiguration.Feature> featuresWith = SerdeFeatures.serializationFeaturesWith(type.getAnnotationMetadata());
        Set<SerdeConfiguration.Feature> featuresWithout = SerdeFeatures.serializationFeaturesWithout(type.getAnnotationMetadata());
        context = context.withFeatures(featuresWith, featuresWithout);
        FormatConfiguration format = FormatConfiguration.from(type.getAnnotationMetadata());
        return format == null ? createSpecificForConfiguration(context, featuresWith, featuresWithout) : createSpecificForFormat(context, type, format);
    }

    @Override
    public Serializer<T> createSpecific(EncoderContext context,
                                        Argument<? extends T> type,
                                        FormatConfiguration format) {
        return createSpecificForFormat(context, type, format);
    }

    @Override
    public Deserializer<T> createSpecific(DecoderContext decoderContext, Argument<? super T> context) throws SerdeException {
        Set<DeserializationConfiguration.Feature> featuresWith = SerdeFeatures.deserializationFeaturesWith(context.getAnnotationMetadata());
        Set<DeserializationConfiguration.Feature> featuresWithout = SerdeFeatures.deserializationFeaturesWithout(context.getAnnotationMetadata());
        decoderContext = decoderContext.withFeatures(featuresWith, featuresWithout);
        FormatConfiguration format = FormatConfiguration.from(context.getAnnotationMetadata());
        return format == null ? createSpecificForConfiguration(decoderContext, featuresWith, featuresWithout) : createSpecificForFormat(decoderContext, context, format);
    }

    @Override
    public Deserializer<T> createSpecific(DecoderContext decoderContext,
                                          Argument<? super T> type,
                                          FormatConfiguration format) throws SerdeException {
        return createSpecificForFormat(decoderContext, type, format);
    }

    private Serializer<T> createSpecificForFormat(EncoderContext encoderContext,
                                                  Argument<? extends T> type,
                                                  FormatConfiguration format) {
        Serializer<T> specific = TemporalSerde.super.createSpecific(encoderContext, type, format);
        if (specific != this) {
            return specific;
        }
        return encoderContext.getSerdeConfiguration()
            .map(configuration -> createSpecificForSerialization(
                stringFormatter(configuration, format, encoderContext.getFeatures()),
                serializationTimeShape(configuration, format.shape()),
                serializationNumericUnit(format, encoderContext.getFeatures()),
                format,
                encoderContext.getFeatures()
            ))
            .orElse(this);
    }

    private Deserializer<T> createSpecificForFormat(DecoderContext decoderContext,
                                                    Argument<? super T> type,
                                                    FormatConfiguration format) throws SerdeException {
        Deserializer<T> specific = TemporalSerde.super.createSpecific(decoderContext, type, format);
        if (specific != this) {
            return specific;
        }
        return decoderContext.getSerdeConfiguration()
            .map(configuration -> createSpecificForDeserialization(
                stringFormatter(configuration, format, Set.of()),
                deserializationTimeShape(configuration, format.shape()),
                deserializationNumericUnit(format, decoderContext.getFeatures()),
                format,
                decoderContext.getFeatures()
            ))
            .orElse(this);
    }

    private DefaultFormattedTemporalSerde<T> createSpecificForConfiguration(EncoderContext encoderContext,
                                                                            Set<SerdeConfiguration.Feature> featuresWith,
                                                                            Set<SerdeConfiguration.Feature> featuresWithout) {
        return encoderContext.getSerdeConfiguration()
            .map(configuration -> createSpecificForSerialization(
                stringFormatter(configuration, FormatConfiguration.EMPTY, encoderContext.getFeatures()),
                configuration.getTimeWriteShape(),
                serializationNumericUnit(configuration.getNumericTimeUnit(), encoderContext.getFeatures(), featuresWith, featuresWithout),
                FormatConfiguration.EMPTY,
                encoderContext.getFeatures()
            ))
            .orElse(this);
    }

    private DefaultFormattedTemporalSerde<T> createSpecificForConfiguration(DecoderContext decoderContext,
                                                                            Set<DeserializationConfiguration.Feature> featuresWith,
                                                                            Set<DeserializationConfiguration.Feature> featuresWithout) {
        return decoderContext.getSerdeConfiguration()
            .map(configuration -> createSpecificForDeserialization(
                stringFormatter(configuration),
                configuration.getTimeWriteShape(),
                deserializationNumericUnit(configuration.getNumericTimeUnit(), decoderContext.getFeatures(), featuresWith, featuresWithout),
                configuration,
                decoderContext.getFeatures()
            ))
            .orElse(this);
    }

    /**
     * Create the same serde with new configuration.
     *
     * @param configuration The new configuration
     * @return The updated serde
     */
    protected DefaultFormattedTemporalSerde<T> createSpecific(SerdeConfiguration configuration) {
        return createSpecific(
            stringFormatter(configuration),
            configuration.getTimeWriteShape(),
            configuration.getNumericTimeUnit()
        );
    }

    /**
     * Create the same serde with a new string formatter and explicit shape-derived values.
     *
     * @param stringFormatter The resolved string formatter
     * @param timeWriteShape  The time write shape
     * @param numericUnit     The numeric time unit
     * @return The updated serde
     */
    protected DefaultFormattedTemporalSerde<T> createSpecific(DateTimeFormatter stringFormatter,
                                                              SerdeConfiguration.TimeShape timeWriteShape,
                                                              SerdeConfiguration.NumericTimeUnit numericUnit) {
        return this;
    }

    /**
     * Create the same serde with a new string formatter and explicit format-derived values.
     *
     * @param stringFormatter The resolved string formatter
     * @param timeWriteShape  The time write shape
     * @param numericUnit     The numeric time unit
     * @param format          The format configuration
     * @return The updated serde
     */
    protected DefaultFormattedTemporalSerde<T> createSpecific(DateTimeFormatter stringFormatter,
                                                              SerdeConfiguration.TimeShape timeWriteShape,
                                                              SerdeConfiguration.NumericTimeUnit numericUnit,
                                                              FormatConfiguration format) {
        return createSpecific(stringFormatter, timeWriteShape, numericUnit);
    }

    /**
     * Create the same serde with serialization format-derived values.
     *
     * @param stringFormatter The resolved string formatter
     * @param timeWriteShape  The time write shape
     * @param numericUnit     The numeric time unit
     * @param format          The format configuration
     * @param features        The active serialization features
     * @return The updated serde
     */
    protected DefaultFormattedTemporalSerde<T> createSpecificForSerialization(DateTimeFormatter stringFormatter,
                                                                              SerdeConfiguration.TimeShape timeWriteShape,
                                                                              SerdeConfiguration.NumericTimeUnit numericUnit,
                                                                              FormatConfiguration format,
                                                                              Set<SerdeConfiguration.Feature> features) {
        return createSpecific(stringFormatter, timeWriteShape, numericUnit, format);
    }

    /**
     * Create the same serde with deserialization format-derived values.
     *
     * @param stringFormatter The resolved string formatter
     * @param timeWriteShape  The time write shape
     * @param numericUnit     The numeric time unit
     * @param format          The format configuration
     * @param features        The active deserialization features
     * @return The updated serde
     */
    protected DefaultFormattedTemporalSerde<T> createSpecificForDeserialization(DateTimeFormatter stringFormatter,
                                                                                SerdeConfiguration.TimeShape timeWriteShape,
                                                                                SerdeConfiguration.NumericTimeUnit numericUnit,
                                                                                FormatConfiguration format,
                                                                                Set<DeserializationConfiguration.Feature> features) {
        return createSpecific(stringFormatter, timeWriteShape, numericUnit, format);
    }

    /**
     * Create the same serde with deserialization configuration-derived values.
     *
     * @param stringFormatter The resolved string formatter
     * @param timeWriteShape  The time write shape
     * @param numericUnit     The numeric time unit
     * @param configuration   The serde configuration
     * @param features        The active deserialization features
     * @return The updated serde
     */
    protected DefaultFormattedTemporalSerde<T> createSpecificForDeserialization(DateTimeFormatter stringFormatter,
                                                                                SerdeConfiguration.TimeShape timeWriteShape,
                                                                                SerdeConfiguration.NumericTimeUnit numericUnit,
                                                                                SerdeConfiguration configuration,
                                                                                Set<DeserializationConfiguration.Feature> features) {
        return createSpecific(stringFormatter, timeWriteShape, numericUnit);
    }

    @Override
    public final void serialize(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException {
        serialize0(encoder, value);
    }

    /**
     * Serialize method, can be overridden to support numeric serialization.
     *
     * @param encoder The encoder
     * @param value   The value to serialize
     */
    void serialize0(Encoder encoder, T value) throws IOException {
        encoder.encodeString(stringFormatter.format(value));
    }

    @Override
    public final T deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super T> type) throws IOException {
        String text = decoder.decodeString();
        try {
            return parseString(text);
        } catch (DateTimeException e) {
            return deserializeFallback(e, text);
        }
    }

    /**
     * Parse a temporal string.
     *
     * @param text The text
     * @return The parsed value
     */
    T parseString(String text) {
        return stringFormatter.parse(text, query());
    }

    /**
     * Fallback to try when parsing as a timestamp fails.
     *
     * @param exc The parse exception, for rethrowing
     * @param s   The input value
     * @return The parsed value
     */
    T deserializeFallback(DateTimeException exc, String s) {
        throw exc;
    }

    private DateTimeFormatter stringFormatter(SerdeConfiguration configuration) {
        return createFormatter(configuration).orElse(defaultStringFormatter());
    }

    private DateTimeFormatter stringFormatter(SerdeConfiguration configuration,
                                              FormatConfiguration format,
                                              Set<SerdeConfiguration.Feature> features) {
        return createFormatter(configuration).orElse(defaultStringFormatter(format, features));
    }

    /**
     * @return The default formatter to use when configuration doesn't define one.
     */
    protected DateTimeFormatter defaultStringFormatter() {
        return stringFormatter;
    }

    /**
     * @param format The format configuration
     * @return The default formatter to use for this format when configuration doesn't define one.
     */
    protected DateTimeFormatter defaultStringFormatter(FormatConfiguration format) {
        return defaultStringFormatter();
    }

    /**
     * @param format   The format configuration
     * @param features The active serialization features
     * @return The default formatter to use for this format when configuration doesn't define one.
     */
    protected DateTimeFormatter defaultStringFormatter(FormatConfiguration format,
                                                       Set<SerdeConfiguration.Feature> features) {
        return defaultStringFormatter(format);
    }
}
