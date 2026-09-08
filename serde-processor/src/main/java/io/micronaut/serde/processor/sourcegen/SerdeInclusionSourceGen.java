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
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.util.GeneratedSerdeInclusionUtil;
import io.micronaut.sourcegen.model.ClassTypeDef;
import io.micronaut.sourcegen.model.ExpressionDef;
import io.micronaut.sourcegen.model.FieldDef;
import io.micronaut.sourcegen.model.StatementDef;
import io.micronaut.sourcegen.model.TypeDef;
import io.micronaut.sourcegen.model.VariableDef;
import org.jspecify.annotations.Nullable;

import javax.lang.model.element.Modifier;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    private static final Method STRING_IS_EMPTY_METHOD = ReflectionUtils.getRequiredMethod(String.class, "isEmpty");

    private static final ClassTypeDef INCLUSION_UTIL_TYPE = ClassTypeDef.of(GeneratedSerdeInclusionUtil.class);
    private static final ClassTypeDef SER_INCLUDE_CLASS_TYPE = ClassTypeDef.of(SerdeConfig.SerInclude.class);
    private static final TypeDef SER_INCLUDE_TYPE = TypeDef.of(SerdeConfig.SerInclude.class);

    private SerdeInclusionSourceGen() {
    }

    /**
     * Resolves the inclusion declared for a property at build time, the way the runtime object
     * serializer resolves it: the property declaration wins over the type, which wins over the
     * package. {@code USE_DEFAULTS} and an absent declaration defer to the runtime configuration and
     * resolve to {@code null}. XML nillable properties are always written.
     *
     * @param element     The serialized type
     * @param property    The property
     * @param keyMetadata The key metadata contributed with the property
     * @return The inclusion resolved at build time, or {@code null} when the configuration decides
     */
    public static SerdeConfig.@Nullable SerInclude resolvePropertyInclude(ClassElement element,
                                                                         PropertyElement property,
                                                                         Map<String, String> keyMetadata) {
        if (Boolean.parseBoolean(keyMetadata.get(SerdeConfig.XML_NILLABLE)) || keyMetadata.containsKey(SerdeConfig.XML_WRAPPER_NILLABLE)) {
            return SerdeConfig.SerInclude.ALWAYS;
        }
        SerdeConfig.SerInclude include = includeValue(property.getAnnotationMetadata())
            .or(() -> property.getReadMethod().flatMap(method -> includeValue(method.getAnnotationMetadata())))
            .or(() -> property.getWriteMethod().flatMap(method -> includeValue(method.getAnnotationMetadata())))
            .or(() -> property.getField().flatMap(field -> includeValue(field.getAnnotationMetadata())))
            .or(() -> includeValue(element.getAnnotationMetadata()))
            .or(() -> includeValue(element.getPackage().getAnnotationMetadata()))
            .orElse(null);
        return include == SerdeConfig.SerInclude.USE_DEFAULTS ? null : include;
    }

    private static Optional<SerdeConfig.SerInclude> includeValue(AnnotationMetadata annotationMetadata) {
        return annotationMetadata.enumValue(SerdeConfig.class, SerdeConfig.INCLUDE, SerdeConfig.SerInclude.class);
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
     * The condition for writing a primitive property under an inclusion resolved at build time.
     * Primitive values are never null and never empty, so only {@code NON_DEFAULT} compares the value.
     *
     * @param aThis         The serializer instance
     * @param include       The inclusion, or {@code null} for the runtime configuration
     * @param type          The property type
     * @param propertyValue The local holding the property value
     * @return The generated condition, or {@code null} when the property is always written
     */
    public static ExpressionDef.@Nullable ConditionExpressionDef shouldSerializePrimitive(VariableDef.This aThis,
                                                                                         SerdeConfig.@Nullable SerInclude include,
                                                                                         ClassElement type,
                                                                                         ExpressionDef propertyValue) {
        if (include == null) {
            return shouldSerializePrimitive(aThis, type, propertyValue);
        }
        return switch (include) {
            case NEVER -> ExpressionDef.falseValue().isTrue();
            case NON_DEFAULT -> primitiveIsDefaultExpression(type, propertyValue).isFalse();
            default -> null;
        };
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
     * The condition for writing a scalar property under an inclusion resolved at build time. The
     * common inclusions compile to a null check; the value-dependent ones call the helper matching the
     * serde that writes the value with the constant inclusion.
     *
     * @param aThis         The serializer instance
     * @param include       The inclusion, or {@code null} for the runtime configuration
     * @param type          The property type
     * @param propertyValue The local holding the property value
     * @return The generated condition, or {@code null} when the property is always written
     */
    public static ExpressionDef.@Nullable ConditionExpressionDef shouldSerializeScalar(VariableDef.This aThis,
                                                                                      SerdeConfig.@Nullable SerInclude include,
                                                                                      ClassElement type,
                                                                                      ExpressionDef propertyValue) {
        if (include == null) {
            return shouldSerializeScalar(aThis, type, propertyValue);
        }
        boolean string = "java.lang.String".equals(type.getName());
        return switch (include) {
            case ALWAYS, USE_DEFAULTS -> null;
            case NEVER -> ExpressionDef.falseValue().isTrue();
            case NON_NULL, NON_ABSENT -> propertyValue.isNonNull();
            case NON_EMPTY -> string
                ? propertyValue.isNonNull().and(propertyValue.invoke(STRING_IS_EMPTY_METHOD).isFalse())
                : propertyValue.isNonNull();
            case NON_DEFAULT -> INCLUSION_UTIL_TYPE.invokeStatic(scalarInclusionMethod(type), includeConstant(include), propertyValue).isTrue();
        };
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

    /**
     * The condition for writing a property through its property serializer under an inclusion
     * resolved at build time.
     *
     * @param aThis         The serializer instance
     * @param include       The inclusion, or {@code null} for the runtime configuration
     * @param context       The encoder context parameter
     * @param serializer    The property serializer field
     * @param propertyValue The local holding the property value
     * @return The generated condition, or {@code null} when the property is always written
     */
    public static ExpressionDef.@Nullable ConditionExpressionDef shouldSerializeValue(VariableDef.This aThis,
                                                                                     SerdeConfig.@Nullable SerInclude include,
                                                                                     VariableDef.MethodParameter context,
                                                                                     ExpressionDef serializer,
                                                                                     ExpressionDef propertyValue) {
        if (include == null) {
            return shouldSerializeValue(aThis, context, serializer, propertyValue);
        }
        return switch (include) {
            case ALWAYS, USE_DEFAULTS -> null;
            case NEVER -> ExpressionDef.falseValue().isTrue();
            case NON_NULL -> propertyValue.isNonNull();
            case NON_ABSENT, NON_EMPTY, NON_DEFAULT -> INCLUSION_UTIL_TYPE.invokeStatic(
                SHOULD_SERIALIZE_METHOD,
                includeConstant(include),
                context,
                serializer,
                propertyValue.cast(TypeDef.OBJECT)
            ).isTrue();
        };
    }

    /**
     * Wraps a write statement in the inclusion condition, or returns it unchanged when the property is
     * always written.
     *
     * @param condition The condition, or {@code null}
     * @param write     The write statement
     * @return The guarded statement
     */
    public static StatementDef guard(ExpressionDef.@Nullable ConditionExpressionDef condition, StatementDef write) {
        return condition == null ? write : condition.ifTrue(write);
    }

    private static ExpressionDef includeConstant(SerdeConfig.SerInclude include) {
        return SER_INCLUDE_CLASS_TYPE.getStaticField(include.name(), SER_INCLUDE_TYPE);
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
