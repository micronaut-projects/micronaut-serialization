package io.micronaut.json;

import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;

/**
 * Utilities for type inference.
 */
public class TypeInference {
    public static Map<String, Argument<?>> inferExact(Argument<?> genericType, Argument<?> forType) {
        if (genericType == null || forType == null) {
            return null;
        }
        final Class<?> gt = genericType.getType();
        if (gt != forType.getType()) {
            if (gt.isPrimitive()) {
                if (ReflectionUtils.getWrapperType(gt) != forType.getType()) {
                    return null;
                }
            } else {
                return null;
            }
        }
        if (!genericType.isTypeVariable() && !genericType.hasTypeVariables()) {
            return Collections.emptyMap();
        }

        if (genericType.isTypeVariable()) {
            return Collections.singletonMap(
                    genericType.getName(),
                    forType
            );
        } else {
            final Argument<?>[] typeParameters = genericType.getTypeParameters();
            final int len = typeParameters.length;
            Argument<?>[] declaredParameters = resolveDeclaredParameters(forType, len);
            if (declaredParameters != null) {
                Map<String, Argument<?>> inferred = new HashMap<>(len);
                for (int i = 0; i < len; i++) {
                    Argument<?> typeParameter = typeParameters[i];
                    final Argument<?> declared = declaredParameters[i];
                    inferred.put(typeParameter.getName(), declared);
                }
                return inferred;
            }
        }
        return null;
    }

    public static Map<String, Argument<?>> inferContravariant(Argument<?> genericType, Argument<?> forType) {
        if (genericType == null || forType == null) {
            return null;
        }
        if (!genericType.getType().isAssignableFrom(forType.getType())) {
            return null;
        }
        if (!genericType.isTypeVariable() && !genericType.hasTypeVariables()) {
            return Collections.emptyMap();
        }

        if (genericType.isTypeVariable()) {
            return Collections.singletonMap(
                    genericType.getName(),
                    forType
            );
        } else {
            final Argument<?>[] typeParameters = genericType.getTypeParameters();
            final int len = typeParameters.length;
            final Argument<?>[] declaredParameters = resolveDeclaredParameters(forType, len);
            if (len == declaredParameters.length) {
                Map<String, Argument<?>> inferred = new HashMap<>(len);
                for (int i = 0; i < len; i++) {
                    Argument<?> typeParameter = typeParameters[i];
                    inferred.put(typeParameter.getName(), declaredParameters[i]);
                }
                return inferred;
            }
        }
        return null;
    }

    private static Argument<?>[] resolveDeclaredParameters(Argument<?> forType, int len) {
        Argument<?>[] declaredParameters = forType.getTypeParameters();
        boolean compatibleLength = len == declaredParameters.length;
        if (!compatibleLength) {
            // try materialize type variables from type
            final TypeVariable<? extends Class<?>>[] declaredParams = forType.getType().getTypeParameters();
            if (declaredParams.length == len) {
                declaredParameters = new Argument[len];
                for (int i = 0; i < declaredParams.length; i++) {
                    TypeVariable<? extends Class<?>> declaredParam = declaredParams[i];
                    final Type[] bounds = declaredParam.getBounds();
                    if (ArrayUtils.isNotEmpty(bounds)) {
                        final Type bound = bounds[0];
                        try {
                            declaredParameters[i] = Argument.of(bound);
                        } catch (IllegalStateException e) {
                            declaredParameters = null;
                            break;
                        }
                    } else {
                        declaredParameters = null;
                        break;
                    }
                }
            }
        }
        return declaredParameters;
    }

    public static boolean isAssignableFrom(Argument<?> hereType, Argument<?> foundType) {
        return hereType.isAssignableFrom(foundType);
    }

    public static Argument<?> foldTypeVariables(Argument<?> into, VariableFold fold) {
        if (into.isTypeVariable()) {
            return Argument.of(fold.apply(into.getName()).getType(), into.getName());
        } else if (into.hasTypeVariables()) {
            final Argument<?>[] typeParameters = into.getTypeParameters();
            final Argument<?>[] folded = new Argument[typeParameters.length];
            for (int i = 0; i < typeParameters.length; i++) {
                Argument<?> typeParameter = typeParameters[i];
                folded[i] = foldTypeVariables(typeParameter, fold);
            }
            return Argument.of(into.getType(), into.getName(), folded);
        }
        return into;
    }

    static Argument<?> foldInferred(Argument<?> into, Map<String, Argument<?>> inferredTypes) {
        VariableFold fold = var -> {
            Argument<?> inferredType = inferredTypes.get(var);
            if (inferredType == null) {
                throw new IllegalArgumentException("Missing inferred variable " + var);
            }
            return inferredType;
        };
        return foldTypeVariables(into, fold);
    }

    @FunctionalInterface
    interface VariableFold {
        /**
         * Fold the given type variable to a new type.
         *
         * @return The folded type, or {@code null} if the generic type this type variable was part of should be
         * replaced by a raw type.
         */
        @Nullable
        Argument<?> apply(@NonNull String variable);
    }
}
