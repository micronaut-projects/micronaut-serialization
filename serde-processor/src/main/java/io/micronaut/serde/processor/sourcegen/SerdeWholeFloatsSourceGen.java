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
package io.micronaut.serde.processor.sourcegen;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.SerializationConfiguration;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.util.GeneratedSerdeWholeFloatsUtil;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.FieldDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;
import io.micronaut.sourcegen.model.VariableDef;

import javax.lang.model.element.Modifier;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Generates the code of {@link SerializationConfiguration.Feature#WRITE_WHOLE_FLOATS_AS_INTEGERS} in a serializer.
 * Whether the feature is on is known at build time for a bean that sets it and is read from the context once, when
 * the serializer is created, for any other: a {@code double} or {@code float} property costs one field read.
 *
 * @since 3.3.0
 */
@Internal
public final class SerdeWholeFloatsSourceGen {
    private static final String FEATURE = SerializationConfiguration.Feature.WRITE_WHOLE_FLOATS_AS_INTEGERS.name();
    private static final String WHOLE_FLOATS_FIELD = "wholeFloats";

    private static final ClassTypeDef UTIL_TYPE = ClassTypeDef.of(GeneratedSerdeWholeFloatsUtil.class);
    private static final Method ENABLED_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeWholeFloatsUtil.class,
        "enabled",
        Serializer.EncoderContext.class
    );
    private static final Method WITH_FEATURE_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeWholeFloatsUtil.class,
        "withFeature",
        Serializer.EncoderContext.class
    );
    private static final Method WITHOUT_FEATURE_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeWholeFloatsUtil.class,
        "withoutFeature",
        Serializer.EncoderContext.class
    );
    private static final Method ENCODE_DOUBLE_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeWholeFloatsUtil.class,
        "encodeDouble",
        Encoder.class,
        double.class
    );
    private static final Method ENCODE_FLOAT_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeWholeFloatsUtil.class,
        "encodeFloat",
        Encoder.class,
        float.class
    );

    private SerdeWholeFloatsSourceGen() {
    }

    /**
     * How a serializer learns whether the feature is on.
     */
    public enum Mode {
        /**
         * The context the serializer is created with decides.
         */
        CONTEXT,
        /**
         * The bean enables the feature.
         */
        ALWAYS,
        /**
         * The bean disables the feature.
         */
        NEVER
    }

    /**
     * Resolves the mode of a bean from its annotations, as the runtime serializer applies them: a feature that is
     * both enabled and disabled is disabled.
     *
     * @param annotationMetadata The annotation metadata of the bean
     * @return The mode
     */
    public static Mode mode(AnnotationMetadata annotationMetadata) {
        if (names(annotationMetadata, SerdeConfig.FEATURES_WITHOUT)) {
            return Mode.NEVER;
        }
        return names(annotationMetadata, SerdeConfig.FEATURES_WITH) ? Mode.ALWAYS : Mode.CONTEXT;
    }

    /**
     * Whether a type or one of its members has feature overrides that the generated serdes do not implement. They
     * implement this feature for a type that sets it. The metadata of a member also exposes the annotations of its
     * type, so a member whose overrides are those of the type has none of its own; a member that sets a feature
     * for itself is written by the runtime serde.
     *
     * @param annotationMetadata The annotation metadata of the type or of a member of it
     * @param typeMetadata       The annotation metadata of the type
     * @return Whether the feature overrides are left to the runtime serde
     */
    public static boolean hasUnsupportedFeatureOverrides(AnnotationMetadata annotationMetadata, AnnotationMetadata typeMetadata) {
        String[] with = annotationMetadata.stringValues(SerdeConfig.class, SerdeConfig.FEATURES_WITH);
        String[] without = annotationMetadata.stringValues(SerdeConfig.class, SerdeConfig.FEATURES_WITHOUT);
        if (with.length == 0 && without.length == 0) {
            return false;
        }
        if (!Arrays.equals(with, typeMetadata.stringValues(SerdeConfig.class, SerdeConfig.FEATURES_WITH))
            || !Arrays.equals(without, typeMetadata.stringValues(SerdeConfig.class, SerdeConfig.FEATURES_WITHOUT))) {
            return true;
        }
        return hasOther(with) || hasOther(without);
    }

    /**
     * @param type The type of a property
     * @return Whether the property is a {@code double} or a {@code float}, primitive or boxed, that the serializer
     * writes without a property serializer
     */
    public static boolean isFloatingPoint(ClassElement type) {
        if (type.isArray()) {
            return false;
        }
        return switch (type.getName()) {
            case "double", "java.lang.Double", "float", "java.lang.Float" -> true;
            default -> false;
        };
    }

    /**
     * The field holding whether the feature is on in the context the serializer was created with.
     *
     * @return The generated field
     */
    public static FieldDef field() {
        return FieldDef.builder(WHOLE_FLOATS_FIELD, TypeDef.Primitive.BOOLEAN)
            .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
            .build();
    }

    /**
     * The constructor statement resolving the feature once per serializer instance.
     *
     * @param aThis   The serializer instance
     * @param context The encoder context constructor parameter
     * @return The generated statement
     */
    public static StatementDef resolveStatement(VariableDef.This aThis, ExpressionDef context) {
        return aThis.field(WHOLE_FLOATS_FIELD, TypeDef.Primitive.BOOLEAN).put(UTIL_TYPE.invokeStatic(ENABLED_METHOD, context));
    }

    /**
     * The context that a bean which sets the feature creates the serializers of its properties with, so that the
     * values it holds are written as the bean is.
     *
     * @param mode    The mode of the bean, not {@link Mode#CONTEXT}
     * @param context The encoder context constructor parameter
     * @return The derived context
     */
    public static ExpressionDef featureContext(Mode mode, ExpressionDef context) {
        return UTIL_TYPE.invokeStatic(mode == Mode.ALWAYS ? WITH_FEATURE_METHOD : WITHOUT_FEATURE_METHOD, context);
    }

    /**
     * Writes a {@code double} or {@code float} property.
     *
     * @param mode         The mode of the bean
     * @param aThis        The serializer instance
     * @param encoder      The encoder
     * @param encodeMethod The method of the encoder that writes the value as it is
     * @param type         The property type
     * @param value        The non-null value
     * @return The generated statement
     */
    public static StatementDef encode(Mode mode,
                                      VariableDef.This aThis,
                                      ExpressionDef encoder,
                                      Method encodeMethod,
                                      ClassElement type,
                                      ExpressionDef value) {
        StatementDef asItIs = encoder.invoke(encodeMethod, value);
        if (mode == Mode.NEVER) {
            return asItIs;
        }
        boolean single = type.getName().equals("float") || type.getName().equals("java.lang.Float");
        StatementDef whole = UTIL_TYPE.invokeStatic(single ? ENCODE_FLOAT_METHOD : ENCODE_DOUBLE_METHOD, encoder, value);
        if (mode == Mode.ALWAYS) {
            return whole;
        }
        return aThis.field(WHOLE_FLOATS_FIELD, TypeDef.Primitive.BOOLEAN).isTrue().ifTrue(whole, asItIs);
    }

    private static boolean names(AnnotationMetadata annotationMetadata, String member) {
        for (String name : annotationMetadata.stringValues(SerdeConfig.class, member)) {
            if (FEATURE.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasOther(String[] names) {
        for (String name : names) {
            if (!FEATURE.equals(name)) {
                return true;
            }
        }
        return false;
    }
}
