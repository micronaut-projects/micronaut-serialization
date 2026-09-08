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

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.util.GeneratedSerdeInclusionUtil;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.FieldDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;
import io.micronaut.sourcegen.model.VariableDef;

import javax.lang.model.element.Modifier;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Generates the inclusion handling shared by the bean and record serializer generators.
 *
 * <p>The active inclusion is resolved once, in the generated constructor, into two final fields: the
 * {@link SerdeConfig.SerInclude} itself and a flag for a configuration that never skips a property.
 * A per-property check is then a field read plus, at most, a static call that switches on the already
 * resolved value.</p>
 *
 * @since 3.2
 */
@Internal
public final class SerdeInclusionSourceGen {
    private static final String INCLUDE_FIELD = "include";
    private static final String INCLUDE_ALL_FIELD = "includeAll";

    private static final Method RESOLVE_INCLUSION_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeInclusionUtil.class,
        "resolveInclusion",
        Serializer.EncoderContext.class
    );
    private static final Method INCLUDE_ALWAYS_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeInclusionUtil.class,
        "includeAlways",
        SerdeConfig.SerInclude.class
    );
    private static final Method SHOULD_SERIALIZE_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeInclusionUtil.class,
        "shouldSerialize",
        SerdeConfig.SerInclude.class,
        Serializer.EncoderContext.class,
        Serializer.class,
        Object.class
    );
    private static final Method SHOULD_SERIALIZE_PRIMITIVE_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeInclusionUtil.class,
        "shouldSerializePrimitive",
        SerdeConfig.SerInclude.class,
        boolean.class
    );
    private static final Method SHOULD_SERIALIZE_STRING_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeInclusionUtil.class,
        "shouldSerializeString",
        SerdeConfig.SerInclude.class,
        String.class
    );
    private static final Method SHOULD_SERIALIZE_NUMBER_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeInclusionUtil.class,
        "shouldSerializeNumber",
        SerdeConfig.SerInclude.class,
        Number.class
    );
    private static final Method SHOULD_SERIALIZE_BOOLEAN_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeInclusionUtil.class,
        "shouldSerializeBoolean",
        SerdeConfig.SerInclude.class,
        Boolean.class
    );
    private static final Method SHOULD_SERIALIZE_CHARACTER_METHOD = ReflectionUtils.getRequiredMethod(
        GeneratedSerdeInclusionUtil.class,
        "shouldSerializeCharacter",
        SerdeConfig.SerInclude.class,
        Character.class
    );
    private static final Method FLOAT_COMPARE_METHOD = ReflectionUtils.getRequiredMethod(Float.class, "compare", float.class, float.class);
    private static final Method DOUBLE_COMPARE_METHOD = ReflectionUtils.getRequiredMethod(Double.class, "compare", double.class, double.class);

    private static final ClassTypeDef INCLUSION_UTIL_TYPE = ClassTypeDef.of(GeneratedSerdeInclusionUtil.class);
    private static final TypeDef SER_INCLUDE_TYPE = TypeDef.of(SerdeConfig.SerInclude.class);

    private SerdeInclusionSourceGen() {
    }

    /**
     * The fields holding the resolved inclusion.
     *
     * @return The generated fields
     */
    public static List<FieldDef> fields() {
        return List.of(
            FieldDef.builder(INCLUDE_FIELD, SER_INCLUDE_TYPE)
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .build(),
            FieldDef.builder(INCLUDE_ALL_FIELD, TypeDef.Primitive.BOOLEAN)
                .addModifiers(Modifier.PRIVATE, Modifier.FINAL)
                .build()
        );
    }

    /**
     * The constructor statements resolving the inclusion once per serializer instance.
     *
     * @param aThis   The serializer instance
     * @param context The encoder context constructor parameter
     * @return The generated statements
     */
    public static List<StatementDef> resolveStatements(VariableDef.This aThis, VariableDef.MethodParameter context) {
        return List.of(
            includeField(aThis).put(INCLUSION_UTIL_TYPE.invokeStatic(RESOLVE_INCLUSION_METHOD, context)),
            aThis.field(INCLUDE_ALL_FIELD, TypeDef.Primitive.BOOLEAN)
                .put(INCLUSION_UTIL_TYPE.invokeStatic(INCLUDE_ALWAYS_METHOD, includeField(aThis)))
        );
    }

    /**
     * The condition for writing a primitive property.
     *
     * @param aThis         The serializer instance
     * @param type          The property type
     * @param propertyValue The local holding the property value
     * @return The generated condition
     */
    public static ExpressionDef.ConditionExpressionDef shouldSerializePrimitive(VariableDef.This aThis,
                                                                               ClassElement type,
                                                                               ExpressionDef propertyValue) {
        return inclusionCheck(aThis, INCLUSION_UTIL_TYPE.invokeStatic(
            SHOULD_SERIALIZE_PRIMITIVE_METHOD,
            includeField(aThis),
            primitiveIsDefaultExpression(type, propertyValue)
        ));
    }

    /**
     * The condition for writing a scalar property encoded without a property serializer.
     *
     * @param aThis         The serializer instance
     * @param type          The property type
     * @param propertyValue The local holding the property value
     * @return The generated condition
     */
    public static ExpressionDef.ConditionExpressionDef shouldSerializeScalar(VariableDef.This aThis,
                                                                            ClassElement type,
                                                                            ExpressionDef propertyValue) {
        return inclusionCheck(aThis, INCLUSION_UTIL_TYPE.invokeStatic(
            scalarInclusionMethod(type),
            includeField(aThis),
            propertyValue
        ));
    }

    /**
     * The condition for writing a property through its property serializer.
     *
     * @param aThis         The serializer instance
     * @param context       The encoder context parameter
     * @param serializer    The property serializer field
     * @param propertyValue The local holding the property value
     * @return The generated condition
     */
    public static ExpressionDef.ConditionExpressionDef shouldSerializeValue(VariableDef.This aThis,
                                                                           VariableDef.MethodParameter context,
                                                                           ExpressionDef serializer,
                                                                           ExpressionDef propertyValue) {
        return inclusionCheck(aThis, INCLUSION_UTIL_TYPE.invokeStatic(
            SHOULD_SERIALIZE_METHOD,
            includeField(aThis),
            context,
            serializer,
            propertyValue.cast(TypeDef.OBJECT)
        ));
    }

    private static VariableDef.Field includeField(VariableDef.This aThis) {
        return aThis.field(INCLUDE_FIELD, SER_INCLUDE_TYPE);
    }

    /**
     * Combines an inclusion check with the precomputed "writes everything" flag so a configuration that
     * never skips a property costs a single field read on the serialization hot path.
     */
    private static ExpressionDef.ConditionExpressionDef inclusionCheck(VariableDef.This aThis, ExpressionDef check) {
        return aThis.field(INCLUDE_ALL_FIELD, TypeDef.Primitive.BOOLEAN).isTrue().or(check.isTrue());
    }

    /**
     * The inclusion helper matching the serde that writes the value at runtime. Every scalar type
     * written without a property serializer other than string, boolean and character is a number.
     */
    private static Method scalarInclusionMethod(ClassElement type) {
        return switch (type.getName()) {
            case "java.lang.String" -> SHOULD_SERIALIZE_STRING_METHOD;
            case "java.lang.Boolean" -> SHOULD_SERIALIZE_BOOLEAN_METHOD;
            case "java.lang.Character" -> SHOULD_SERIALIZE_CHARACTER_METHOD;
            default -> SHOULD_SERIALIZE_NUMBER_METHOD;
        };
    }

    /**
     * Whether a primitive value equals the default value the matching serde reports for its type.
     * Float and double go through {@code compare} so {@code -0.0} and {@code NaN} agree with
     * {@code FloatSerde}/{@code DoubleSerde}, which compare the boxed value using {@code equals}.
     */
    private static ExpressionDef primitiveIsDefaultExpression(ClassElement type, ExpressionDef propertyValue) {
        return switch (type.getName()) {
            case "boolean" -> propertyValue.isFalse();
            case "char" -> isEqual(propertyValue, ExpressionDef.constant(0).cast(TypeDef.Primitive.CHAR));
            case "float" -> isEqual(
                ClassTypeDef.of(Float.class).invokeStatic(FLOAT_COMPARE_METHOD, propertyValue, ExpressionDef.constant(0F)),
                ExpressionDef.constant(0)
            );
            case "double" -> isEqual(
                ClassTypeDef.of(Double.class).invokeStatic(DOUBLE_COMPARE_METHOD, propertyValue, ExpressionDef.constant(0D)),
                ExpressionDef.constant(0)
            );
            case "long" -> isEqual(propertyValue, ExpressionDef.constant(0L));
            case "byte" -> isEqual(propertyValue, ExpressionDef.constant(0).cast(TypeDef.Primitive.BYTE));
            case "short" -> isEqual(propertyValue, ExpressionDef.constant(0).cast(TypeDef.Primitive.SHORT));
            default -> isEqual(propertyValue, ExpressionDef.constant(0));
        };
    }

    private static ExpressionDef isEqual(ExpressionDef left, ExpressionDef right) {
        return left.compare(ExpressionDef.ComparisonOperation.OpType.EQUAL_TO, right);
    }
}
