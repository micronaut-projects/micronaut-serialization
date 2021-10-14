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

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@BootstrapContextCompatible
@Singleton
public final class SerdeRegistry {
    private final BeanContext context;

    private final Registry<Serializer<?>> serializers;
    private final Registry<Deserializer<?>> deserializers;

    @Inject
    @SuppressWarnings({"rawtypes", "unchecked"})
    SerdeRegistry(BeanContext context) {
        this.context = context;
        this.serializers = new Registry(Serializer.class, Serializer.Factory.class);
        this.deserializers = new Registry(Deserializer.class, Deserializer.Factory.class);
    }

    //region API methods

    public <T> Deserializer<T> findInvariantDeserializer(Type forType) {
        return this.<T>findInvariantDeserializerProvider(forType).get();
    }

    public <T> Deserializer<T> findInvariantDeserializer(Class<T> forType) {
        return findInvariantDeserializer((Type) forType);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> Provider<Deserializer<T>> findInvariantDeserializerProvider(Type forType) {
        return (Provider) deserializers.findInvariant(forType);
    }

    public <T> Serializer<? super T> findContravariantSerializer(Type forType) {
        return findContravariantSerializerProvider(forType).get();
    }

    public <T> Serializer<? super T> findContravariantSerializer(Class<T> forType) {
        return findContravariantSerializer((Type) forType);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> Provider<Serializer<? super T>> findContravariantSerializerProvider(Type forType) {
        return (Provider) serializers.findContravariant(forType);
    }

    //endregion

    private static Type foldInferred(Type into, Map<String, Type> inferredTypes) {
        TypeInference.VariableFold fold = var -> {
            Type inferredType = inferredTypes.get(var);
            if (inferredType == null) {
                throw new IllegalArgumentException("Missing inferred variable " + var);
            }
            return inferredType;
        };
        return TypeInference.foldTypeVariables(into, fold);
    }

    private static Type normalizePrimitiveType(Type t) {
        if (t instanceof Argument) {
            final Class<?> type = ((Argument<?>) t).getType();
            return ReflectionUtils.getWrapperType(type);
        }
        if (t instanceof Class<?>) {
            return ReflectionUtils.getPrimitiveType((Class<?>) t);
        } else {
            return t;
        }
    }

    private class Registry<S> {
        private final Collection<FactoryWrapper<S>> factories;

        Registry(Class<S> baseClass, Class<? extends BaseCodecFactory> factoryClass) {
            Collection<BaseCodecFactory> factories = new ArrayList<>(context.getBeansOfType(factoryClass));
            for (BeanDefinition<S> directDefinition : context.getBeanDefinitions(baseClass)) {
                factories.add(new ContainerSyntheticFactory<>(directDefinition, baseClass));
            }
            //noinspection Convert2MethodRef
            this.factories = factories.stream()
                    .map(factory -> new FactoryWrapper<S>(factory))
                    .collect(Collectors.toList());
        }

        public Provider<S> findContravariant(Type forType) {
            forType = normalizePrimitiveType(forType);

            Provider<S> found = null;
            Type foundType = null;
            for (FactoryWrapper<S> factory : factories) {
                Map<String, Type> inferred = TypeInference.inferContravariant(factory.genericType, forType);
                 if (inferred != null) {
                    Type hereType;
                    if (inferred.isEmpty()) {
                        hereType = factory.genericType;
                    } else {
                        hereType = foldInferred(factory.genericType, inferred);
                    }
                    if (found != null && foundType != null && hereType != null && TypeInference.isAssignableFrom(hereType, foundType, true)) {
                        // hereType :> foundType :> type, foundType is the better choice
                        continue;
                    }

                    foundType = hereType;
                    found = () -> factory.createFromInference(inferred);
                }
            }
            if (found != null) {
                return found;
            }
            throw new AssertionError("Shouldn't happen, ObjectSerializer should always match. Maybe the classpath is broken?");
        }

        public Provider<S> findInvariant(Type forType) {
            forType = normalizePrimitiveType(forType);

            for (FactoryWrapper<S> factory : factories) {
                Map<String, Type> inferred = TypeInference.inferExact(factory.genericType, forType);
                if (inferred != null) {
                    return () -> factory.createFromInference(inferred);
                }
            }
            throw new NoSuchDeserializerException("No deserializer found for type " + forType.getTypeName());
        }
    }

    private static class NoSuchDeserializerException extends RuntimeException {
        public NoSuchDeserializerException(String message) {
            super(message);
        }
    }

    private class FactoryWrapper<S> {
        private final BaseCodecFactory factory;

        @Nullable
        private final ConcurrentMap<TypeVariableAssignment, S> instances;
        private volatile S singleton = null;

        final Type genericType;

        FactoryWrapper(BaseCodecFactory factory) {
            this.factory = factory;
            this.genericType = factory.getGenericType();
            if (TypeInference.hasFreeVariables(genericType)) {
                instances = new ConcurrentHashMap<>();
            } else {
                instances = null;
            }
        }

        S createFromInference(Map<String, Type> assignment) {
            return create(assignment.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        }

        S create(Map<String, Type> assignment) {
            if (assignment.isEmpty()) {
                S res = singleton;
                if (res == null) {
                    singleton = res = newInstanceSafe(TypeVariableAssignment.EMPTY);
                }
                return res;
            } else {
                assert instances != null;
                TypeVariableAssignment assignmentWrapper = new TypeVariableAssignment(assignment);
                S res = instances.get(assignmentWrapper);
                if (res == null) {
                    res = newInstanceSafe(assignmentWrapper);
                    instances.put(assignmentWrapper, res);
                }
                return res;
            }
        }

        @SuppressWarnings("unchecked")
        private S newInstanceSafe(TypeVariableAssignment empty) {
            return (S) factory.newInstance(SerdeRegistry.this, empty);
        }
    }

    /**
     * {@link BaseCodecFactory} implementation created for (de)serializers that are in the bean context without a
     * factory.
     */
    private class ContainerSyntheticFactory<S> implements BaseCodecFactory {
        private final BeanDefinition<? extends S> definition;
        private final Type genericType;

        ContainerSyntheticFactory(BeanDefinition<? extends S> definition, Class<S> baseClass) {
            this.definition = definition;
            final List<Argument<?>> typeArguments = definition.getTypeArguments(baseClass);
            if (typeArguments.isEmpty()) {
                throw new IllegalStateException("Bean " + definition.getName() + " implements " + baseClass.getSimpleName() + " as a raw type, this is forbidden.");
            }
            this.genericType = typeArguments.iterator().next();
        }

        @Override
        public Type getGenericType() {
            return genericType;
        }

        @Override
        public S newInstance(SerdeRegistry locator, Function<String, Type> getTypeParameter) {
            return context.getBean(definition);
        }
    }

    private static class TypeVariableAssignment implements Function<String, Type> {
        static final TypeVariableAssignment EMPTY = new TypeVariableAssignment(Collections.emptyMap());

        private final Map<String, Type> assignment;
        private final int hash;

        TypeVariableAssignment(Map<String, Type> assignment) {
            this.assignment = assignment;
            int hash = 1;
            for (Map.Entry<String, Type> entry : assignment.entrySet()) {
                hash = 31 * 31 * hash + 31 * entry.getKey().hashCode() + TypeInference.typeHashCode(entry.getValue());
            }
            this.hash = hash;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }

            return o instanceof TypeVariableAssignment &&
                    ((TypeVariableAssignment) o).hash == this.hash &&
                    this.assignment.keySet().equals(((TypeVariableAssignment) o).assignment.keySet()) &&
                    this.assignment.entrySet().stream().allMatch(entry -> TypeInference.typesEqual(entry.getValue(), ((TypeVariableAssignment) o).assignment.get(entry.getKey())));
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public Type apply(String s) {
            Type type = assignment.get(s);
            if (type == null) {
                throw new IllegalStateException("Unexpected type variable " + s);
            }
            return type;
        }
    }
}
