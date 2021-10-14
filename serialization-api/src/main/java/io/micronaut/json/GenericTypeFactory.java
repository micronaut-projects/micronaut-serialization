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

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;

import java.lang.reflect.*;
import java.util.Map;
import java.util.Objects;

public class GenericTypeFactory {
    private GenericTypeFactory() {}

    public static Type makeArrayType(@NonNull Type component) {
        Objects.requireNonNull(component, "component");

        if (component instanceof Class<?>) {
            return Array.newInstance((Class<?>) component, 0).getClass();
        } else if (component instanceof GenericArrayType ||
                component instanceof ParameterizedType ||
                component instanceof WildcardType ||
                component instanceof TypeVariable) {
            return new GenericArrayTypeImpl(component);
        } else {
            throw new IllegalArgumentException("Can't create an array type from " + component.getTypeName());
        }
    }

    public static ParameterizedType makeParameterizedTypeWithOwner(@Nullable Type owner, @NonNull Class<?> raw, Type... args) {
        Objects.requireNonNull(raw, "raw");
        Objects.requireNonNull(args, "args");

        return new ParameterizedTypeImpl(owner, raw, args);
    }

    public static WildcardType makeWildcardType(@NonNull Type[] upper, @NonNull Type[] lower) {
        Objects.requireNonNull(upper, "upper");
        Objects.requireNonNull(lower, "lower");
        return new WildcardTypeImpl(upper, lower);
    }

    private static class GenericArrayTypeImpl implements GenericArrayType {
        private final Type component;

        GenericArrayTypeImpl(@NonNull Type component) {
            this.component = component;
        }

        @Override
        public Type getGenericComponentType() {
            return component;
        }

        @Override
        public String toString() {
            return component.getTypeName() + "[]";
        }
    }

    private static class ParameterizedTypeImpl implements ParameterizedType {
        private final Type owner;
        private final Type raw;
        private final Type[] args;

        ParameterizedTypeImpl(@Nullable Type owner, Class<?> raw, Type[] args) {
            this.args = args;
            this.raw = raw;
            this.owner = owner == null ? raw.getEnclosingClass() : owner;
        }

        @Override
        public Type[] getActualTypeArguments() {
            return args.clone();
        }

        @Override
        public Type getRawType() {
            return raw;
        }

        @Override
        public Type getOwnerType() {
            return owner;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            if (owner != null) {
                builder.append(owner.getTypeName()).append('.');
            }
            builder.append(raw.getTypeName()).append('<');
            for (int i = 0; i < args.length; i++) {
                if (i != 0) {
                    builder.append(", ");
                }
                builder.append(args[i].getTypeName());
            }
            builder.append('>');
            return builder.toString();
        }
    }

    private static class WildcardTypeImpl implements WildcardType {
        private final Type[] upper;
        private final Type[] lower;

        private WildcardTypeImpl(@NonNull Type[] upper, @NonNull Type[] lower) {
            this.upper = upper;
            this.lower = lower;
        }

        @Override
        public Type[] getUpperBounds() {
            return upper.clone();
        }

        @Override
        public Type[] getLowerBounds() {
            return lower.clone();
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder("?");
            for (Type type : upper) {
                if (type != Object.class) {
                    builder.append(" extends ").append(type.getTypeName());
                }
            }
            for (Type type : lower) {
                builder.append(" super ").append(type.getTypeName());
            }
            return builder.toString();
        }
    }
}
