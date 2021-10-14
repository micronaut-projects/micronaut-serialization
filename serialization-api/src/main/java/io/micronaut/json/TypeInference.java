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
package io.micronaut.json;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

class TypeInference {
    private static final Class<?> RECORD_CLASS;

    static {
        Class<?> cl;
        try {
            cl = Class.forName("java.lang.Record");
        } catch (ClassNotFoundException e) {
            cl = null;
        }
        RECORD_CLASS = cl;
    }

    @Nullable
    static Map<String, Type> inferExact(Type freeType, Type targetType) {
        Map<String, Type> map = new HashMap<>();
        if (!inferRecursive(map, freeType, targetType)) {
            return null;
        }
        return map;
    }

    @Nullable
    static Map<String, Type> inferContravariant(Type freeType, Type targetType) {
        Type parameterization = findParameterization(targetType, getErasure(freeType));
        if (parameterization == null) {
            return null;
        }
        return inferExact(freeType, parameterization);
    }

    private static boolean hasFreeVariables(Type[] types) {
        for (Type type : types) {
            if (hasFreeVariables(type)) {
                return true;
            }
        }
        return false;
    }

    static boolean hasFreeVariables(@Nullable Type type) {
        if (type instanceof Argument) {
            Argument<?> argument = (Argument<?>) type;
            if (argument.isTypeVariable()) {
                return true;
            } else {
                for (Argument<?> typeParameter : argument.getTypeParameters()) {
                    if (typeParameter.isTypeVariable()) {
                        return true;
                    }
                }
            }
        }
        if (type instanceof TypeVariable) {
            return true;
        } else if (type instanceof GenericArrayType) {
            return hasFreeVariables(((GenericArrayType) type).getGenericComponentType());
        } else if (type instanceof ParameterizedType) {
            return hasFreeVariables(((ParameterizedType) type).getOwnerType()) ||
                    hasFreeVariables(((ParameterizedType) type).getRawType()) ||
                    hasFreeVariables(((ParameterizedType) type).getActualTypeArguments());
        } else if (type instanceof WildcardType) {
            return hasFreeVariables(((WildcardType) type).getUpperBounds()) || hasFreeVariables(((WildcardType) type).getLowerBounds());
        } else {
            return false;
        }
    }

    private static boolean inferRecursive(Map<String, Type> inferred, Type[] generic, Type[] target) {
        if (generic.length != target.length) {
            return false;
        }
        for (int i = 0; i < generic.length; i++) {
            if (!inferRecursive(inferred, generic[i], target[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean inferRecursive(Map<String, Type> inferred, @Nullable Type generic, @Nullable Type target) {
        if (generic == null) {
            return target == null;
        } else if (generic instanceof TypeVariable<?>) {
            Type existing = inferred.put(((TypeVariable<?>) generic).getName(), target);
            return existing == null || existing.equals(target);
        } else if (generic instanceof ParameterizedType) {
            return target instanceof ParameterizedType &&
                    ((ParameterizedType) generic).getRawType().equals(((ParameterizedType) target).getRawType()) &&
                    (
                            !isInnerClass((Class<?>) ((ParameterizedType) generic).getRawType()) || inferRecursive(inferred,
                                                                                                                   ((ParameterizedType) generic).getOwnerType(),
                                                                                                                   ((ParameterizedType) target).getOwnerType())) &&
                    inferRecursive(inferred,
                                   ((ParameterizedType) generic).getActualTypeArguments(),
                                   ((ParameterizedType) target).getActualTypeArguments());
        } else if (generic instanceof WildcardType) {
            return target instanceof WildcardType &&
                    inferRecursive(inferred,
                                   ((WildcardType) generic).getUpperBounds(),
                                   ((WildcardType) target).getUpperBounds()) &&
                    inferRecursive(inferred, ((WildcardType) generic).getLowerBounds(), ((WildcardType) target).getLowerBounds());
        } else if (generic instanceof GenericArrayType) {
            if (target instanceof Class<?> && ((Class<?>) target).isArray()) {
                return inferRecursive(inferred,
                                      ((GenericArrayType) generic).getGenericComponentType(),
                                      ((Class<?>) target).getComponentType());
            }

            return target instanceof GenericArrayType &&
                    inferRecursive(inferred,
                                   ((GenericArrayType) generic).getGenericComponentType(),
                                   ((GenericArrayType) target).getGenericComponentType());
        } else if (generic instanceof Class<?>) {
            if (target instanceof Argument) {
                return generic == ((Argument<?>) target).getType();
            } else {
                return generic == target;
            }
        } else if (generic instanceof Argument) {
            final Argument<?> argument = (Argument<?>) generic;
            if (target instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) target;
                final Type rawType = pt.getRawType();
                if (argument.getType() == rawType) {
                    final Type[] args = pt.getActualTypeArguments();
                    final Argument<?>[] genericParameters = argument.getTypeParameters();
                    if (args.length == genericParameters.length) {
                        for (int i = 0; i < args.length; i++) {
                            final Argument<?> targetGeneric = Argument.of(args[i]);
                            final Argument<?> gt = genericParameters[i];
                            final boolean isTypeVar = gt.isTypeVariable();
                            if (isTypeVar && !gt.isAssignableFrom(targetGeneric)) {
                                return false;
                            } else if (!isTypeVar && !gt.equalsType(targetGeneric)) {
                                return false;
                            } else {
                                inferred.put(gt.getName(), targetGeneric);
                            }
                        }
                    }
                    return true;
                }
            }
            return argument.getType() == target;
        } else {
            throw new UnsupportedOperationException(generic.getClass().getName());
        }
    }

    @Internal
    @Nullable
    public static Type foldTypeVariables(Type into, VariableFold fold) {
        if (into instanceof GenericArrayType) {
            Type component = foldTypeVariables(((GenericArrayType) into).getGenericComponentType(), fold);
            // erased type
            return component == null ? Object[].class : GenericTypeFactory.makeArrayType(component);
        } else if (into instanceof ParameterizedType) {
            ParameterizedType t = (ParameterizedType) into;
            Type owner = t.getOwnerType() == null ? null : foldTypeVariables(t.getOwnerType(), fold);
            Type raw = foldTypeVariables(t.getRawType(), fold);
            Type[] args = Arrays.stream(t.getActualTypeArguments()).map(arg -> foldTypeVariables(arg, fold)).toArray(Type[]::new);
            if (Arrays.asList(args).contains(null)) {
                // erased type
                return raw;
            } else {
                return GenericTypeFactory.makeParameterizedTypeWithOwner(owner, (Class<?>) raw, args);
            }
        } else if (into instanceof TypeVariable<?>) {
            return fold.apply(((TypeVariable<?>) into).getName());
        } else if (into instanceof WildcardType) {
            WildcardType t = (WildcardType) into;
            Type[] lower = Arrays.stream(t.getLowerBounds()).map(arg -> foldTypeVariables(arg, fold)).toArray(Type[]::new);
            Type[] upper = Arrays.stream(t.getUpperBounds()).map(arg -> foldTypeVariables(arg, fold)).toArray(Type[]::new);
            if (Arrays.asList(lower).contains(null) || Arrays.asList(upper).contains(null)) {
                // erase type
                return null;
            } else {
                return GenericTypeFactory.makeWildcardType(upper, lower);
            }
        } else if (into instanceof Class) {
            return into;
        } else {
            throw new UnsupportedOperationException("Unsupported type for folding: " + into.getClass());
        }
    }

    static Class<?> getErasure(Type type) {
        if (type instanceof Argument) {
            return ((Argument<?>) type).getType();
        }
        if (type instanceof Class) {
            return (Class<?>) type;
        } else if (type instanceof GenericArrayType) {
            return Array.newInstance(getErasure(((GenericArrayType) type).getGenericComponentType()), 0).getClass();
        } else if (type instanceof ParameterizedType) {
            return getErasure(((ParameterizedType) type).getRawType());
        } else if (type instanceof WildcardType) {
            return getErasure(((WildcardType) type).getUpperBounds()[0]);
        } else if (type instanceof TypeVariable) {
            return getErasure(((TypeVariable<?>) type).getBounds()[0]);
        } else {
            throw new UnsupportedOperationException(type.getClass().getName());
        }
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
        Type apply(@NonNull String variable);
    }

    /**
     * Find the parameterization of a raw type on a type. For example, if {@code on} is {@code List<String>}, and
     * {@code of} is {@code Iterable.class}, this method will return a type corresponding to {@code Iterable<String>}.
     *
     * @param child The type to look on for the parameterization
     * @param parent The raw type to look for
     * @return One of: A {@link ParameterizedType} with the raw type being {@code of}, the original value of {@code of}
     * if {@code on} only implements {@code of} as a raw type, or {@code null} if {@code on} does not implement
     * {@code of}.
     */
    @Nullable
    static Type findParameterization(Type child, Class<?> parent) {
        if (child instanceof Argument) {
            Argument<?> argument = (Argument<?>) child;
            if (argument.hasTypeVariables()) {
                child = argument.asParameterizedType();
            } else {
                child = argument.getType();
            }
        }
        if (child instanceof GenericArrayType) {
            if (parent == Object.class || parent == Cloneable.class || parent == Serializable.class) {
                return parent;
            }
            if (!parent.isArray()) {
                return null;
            }
            Type componentType = ((GenericArrayType) child).getGenericComponentType();
            Type componentParameterization = findParameterization(componentType, parent.getComponentType());
            return componentParameterization == null ? null : GenericTypeFactory.makeArrayType(componentParameterization);
        } else if (child instanceof ParameterizedType) {
            ParameterizedType onT = (ParameterizedType) child;
            Class<?> rawType = (Class<?>) onT.getRawType();
            if (rawType == parent) {
                return onT;
            } else {
                Map<TypeVariable<?>, Type> typesToFold = new HashMap<>();
                findFoldableTypes(typesToFold, onT);
                return findParameterization(rawType, t -> foldTypeVariables(t, typesToFold::get), parent);
            }
        } else if (child instanceof TypeVariable<?>) {
            for (Type bound : ((TypeVariable<?>) child).getBounds()) {
                Type boundParameterization = findParameterization(bound, parent);
                if (boundParameterization != null) {
                    return boundParameterization;
                }
            }
            return null;
        } else if (child instanceof WildcardType) {
            for (Type upperBound : ((WildcardType) child).getUpperBounds()) {
                Type boundParameterization = findParameterization(upperBound, parent);
                if (boundParameterization != null) {
                    return boundParameterization;
                }
            }
            return null;
        } else if (child instanceof Class<?>) {
            if (child == parent) {
                return child;
            } else {
                // replace any type variables with raw types
                return findParameterization((Class<?>) child, t -> foldTypeVariables(t, v -> null), parent);
            }
        } else {
            throw new UnsupportedOperationException("Unsupported type for resolution: " + child.getClass());
        }
    }

    static Type findParameterization(Class<?> child, Function<Type, Type> foldFunction, Class<?> parent) {
        if (child.isArray()) {
            if (parent == Serializable.class || parent == Cloneable.class) {
                return parent;
            } else if (parent.isArray()) {
                Type componentParameterization = findParameterization(child.getComponentType(),
                                                                      foldFunction,
                                                                      parent.getComponentType());
                return componentParameterization == null ? null : GenericTypeFactory.makeArrayType(componentParameterization);
            } else {
                return null;
            }
        }
        // special case Object, because Object is also a supertype of interfaces but does not appear in
        // getGenericSuperclass for those
        if (parent == Object.class && !child.isPrimitive()) {
            return Object.class;
        }
        if (parent.isInterface()) {
            for (Type itf : child.getGenericInterfaces()) {
                Type parameterization = findParameterization(foldFunction.apply(itf), parent);
                if (parameterization != null) {
                    return parameterization;
                }
            }
        }
        if (child.getGenericSuperclass() != null) {
            return findParameterization(foldFunction.apply(child.getGenericSuperclass()), parent);
        } else {
            return null;
        }
    }

    private static void findFoldableTypes(Map<TypeVariable<?>, Type> typesToFold, ParameterizedType parameterizedType) {
        TypeVariable<?>[] typeParameters = ((Class<?>) parameterizedType.getRawType()).getTypeParameters();
        Type[] typeArguments = parameterizedType.getActualTypeArguments();
        for (int i = 0; i < typeParameters.length; i++) {
            typesToFold.put(typeParameters[i], typeArguments[i]);
        }
        if (parameterizedType.getOwnerType() instanceof ParameterizedType) {
            findFoldableTypes(typesToFold, (ParameterizedType) parameterizedType.getOwnerType());
        }
    }

    static boolean typesEqual(@Nullable Type left, @Nullable Type right) {
        if (left == right) {
            return true;
        } else if (left == null || right == null) {
            return false;
        } else if (left.equals(right)) {
            return true;
        } else if (left instanceof GenericArrayType) {
            return right instanceof GenericArrayType &&
                    typesEqual(((GenericArrayType) left).getGenericComponentType(),
                               ((GenericArrayType) right).getGenericComponentType());
        } else if (left instanceof ParameterizedType) {
            return right instanceof ParameterizedType &&
                    (
                            !isInnerClass((Class<?>) ((ParameterizedType) left).getRawType()) || typesEqual(((ParameterizedType) left).getOwnerType(),
                                                                                                            ((ParameterizedType) right).getOwnerType())) &&
                    typesEqual(((ParameterizedType) left).getRawType(), ((ParameterizedType) right).getRawType()) &&
                    typesEqual(((ParameterizedType) left).getActualTypeArguments(),
                               ((ParameterizedType) right).getActualTypeArguments());
        } else if (left instanceof TypeVariable<?> || left instanceof Class<?>) {
            // covered in equals above
            return false;
        } else if (left instanceof WildcardType) {
            if (right instanceof WildcardType) {
                return typesEqual(((WildcardType) left).getLowerBounds(), ((WildcardType) right).getLowerBounds()) &&
                        typesEqual(((WildcardType) left).getUpperBounds(), ((WildcardType) right).getUpperBounds());
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    private static final int HASH_CODE_RANDOMIZER = ThreadLocalRandom.current().nextInt();

    static int typeHashCode(@Nullable Type type) {
        return typeHashCode0(type) ^ HASH_CODE_RANDOMIZER;
    }

    private static int typeHashCode0(@Nullable Type type) {
        if (type == null) {
            return 0;
        } else if (type instanceof GenericArrayType) {
            return 31 + typeHashCode0(((GenericArrayType) type).getGenericComponentType());
        } else if (type instanceof ParameterizedType) {
            return 31 * 31 * 31 * 2 +
                    31 * 31 * typeHashCode0(isInnerClass((Class<?>) ((ParameterizedType) type).getRawType())
                                                    ? ((ParameterizedType) type).getOwnerType()
                                                    : null) +
                    31 * typeHashCode0(((ParameterizedType) type).getRawType()) +
                    typeHashCode0(((ParameterizedType) type).getActualTypeArguments());
        } else if (type instanceof TypeVariable<?>) {
            return 31 * 31 * 3 +
                    31 * ((TypeVariable<?>) type).getGenericDeclaration().hashCode() +
                    type.getTypeName().hashCode();
        } else if (type instanceof WildcardType) {
            return 31 * 31 * 4 +
                    31 * typeHashCode0(((WildcardType) type).getUpperBounds()) +
                    typeHashCode0(((WildcardType) type).getLowerBounds());
        } else if (type instanceof Class) {
            return type.hashCode();
        } else if (type instanceof Argument) {
            return ((Argument<?>) type).typeHashCode();
        } else {
            throw new IllegalArgumentException("Unsupported type: " + type.getClass().getName());
        }
    }

    private static int typeHashCode0(Type[] types) {
        int v = 1;
        for (Type t : types) {
            v = 31 * v + typeHashCode0(t);
        }
        return v;
    }

    private static boolean typesEqual(Type[] left, Type[] right) {
        if (left.length != right.length) {
            return false;
        }
        for (int i = 0; i < left.length; i++) {
            if (!typesEqual(left[i], right[i])) {
                return false;
            }
        }
        return true;
    }

    static boolean isAssignableFrom(@NonNull Type to, @NonNull Type from) {
        return isAssignableFrom(to, from, false);
    }

    /**
     * @param antisymmetric If true, the relation of this method must be <i>antisymmetric</i>, meaning that there are
     *                      no two distinct types S, T so that {@code S <: T} and {@code T <: S}. Normal java assignment
     *                      rules are not antisymmetric, e.g. {@code List} and {@code List<String>} are assignable in
     *                      both directions.
     */
    @Internal
    static boolean isAssignableFrom(@NonNull Type to, @NonNull Type from, boolean antisymmetric) {
        if (to instanceof Argument) {
            Argument<?> argument = (Argument<?>) to;
            if (argument.hasTypeVariables()) {
                to = argument.asParameterizedType();
            } else {
                to = argument.getType();
            }
        }
        if (to instanceof GenericArrayType) {
            // antisymmetry invariant is maintained by the recursive call.
            if (from instanceof Class<?>) {
                return ((Class<?>) from).isArray() &&
                        isAssignableFrom(((GenericArrayType) to).getGenericComponentType(),
                                         ((Class<?>) from).getComponentType(),
                                         antisymmetric);
            } else {
                return from instanceof GenericArrayType &&
                        isAssignableFrom(((GenericArrayType) to).getGenericComponentType(),
                                         ((GenericArrayType) from).getGenericComponentType(),
                                         antisymmetric);
            }
        } else if (to instanceof ParameterizedType) {
            ParameterizedType toT = (ParameterizedType) to;
            Class<?> erasure = (Class<?>) toT.getRawType();
            // find the parameterization of the same type, if any exists.
            Type fromParameterization = findParameterization(from, erasure);
            if (fromParameterization == null) {
                // raw types aren't compatible
                return false;
            }
            if (fromParameterization instanceof Class<?>) {
                // in normal java rules, raw types are assignable to parameterized types
                // if we need antisymmetry, we don't allow assignment in this direction
                return !antisymmetric;
            }
            ParameterizedType fromParameterizationT = (ParameterizedType) fromParameterization;
            if (toT.getOwnerType() != null && isInnerClass(erasure)) {
                if (fromParameterizationT.getOwnerType() == null) {
                    return false;
                }
                if (!isAssignableFrom(toT.getOwnerType(), fromParameterizationT.getOwnerType(), antisymmetric)) {
                    return false;
                }
            }
            Type[] toArgs = toT.getActualTypeArguments();
            Type[] fromArgs = fromParameterizationT.getActualTypeArguments();
            for (int i = 0; i < toArgs.length; i++) {
                if (toArgs[i] instanceof WildcardType) {
                    if (!contains((WildcardType) toArgs[i], fromArgs[i])) {
                        return false;
                    }
                } else {
                    if (!typesEqual(toArgs[i], fromArgs[i])) {
                        return false;
                    }
                }
            }
            return true;
        } else if (to instanceof Class<?>) {
            // to is a raw type
            // wrt antisymmetry, if `from` is parameterized, we consider it assignable to `to`.
            return findParameterization(from, (Class<?>) to) != null;
        } else {
            throw new IllegalArgumentException("Not a valid assignment target: " + to);
        }
    }

    private static boolean contains(WildcardType wildcard, Type type) {
        for (Type upperBound : wildcard.getUpperBounds()) {
            if (!isAssignableFrom(upperBound, type)) {
                return false;
            }
        }
        for (Type lowerBound : wildcard.getLowerBounds()) {
            if (!isAssignableFrom(type, lowerBound)) {
                return false;
            }
        }
        return true;
    }

    static boolean isInnerClass(Class<?> cls) {
        Class<?> enclosingClass = cls.getEnclosingClass();
        return enclosingClass != null &&
                !Modifier.isStatic(cls.getModifiers()) &&
                !cls.isInterface() &&
                !cls.isEnum() &&
                !enclosingClass.isInterface() &&
                // can't use isRecord, but this check should do at least for valid java code
                // isRecord does additional checks
                cls.getSuperclass() != RECORD_CLASS;
    }

    static Type parameterizeWithFreeVariables(Class<?> cl) {
        Type owner = isInnerClass(cl) ? parameterizeWithFreeVariables(cl.getEnclosingClass()) : null;
        TypeVariable<? extends Class<?>>[] typeParameters = cl.getTypeParameters();
        if ((owner == null || owner instanceof Class) && typeParameters.length == 0) {
            return cl;
        } else {
            return GenericTypeFactory.makeParameterizedTypeWithOwner(owner, cl, typeParameters);
        }
    }
}
