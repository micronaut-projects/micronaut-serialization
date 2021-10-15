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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.BeanDefinition;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@BootstrapContextCompatible
@Singleton
@Internal
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
    public Deserializer findDeserializer(Argument<?> forType) {
        return deserializers.findInvariant(forType).get();
    }

    public <T> Provider<Deserializer<T>> findDeserializerProvider(Argument<?> forType) {
        return (Provider) deserializers.findInvariant(forType);
    }

    public <T> Deserializer<T> findDeserializer(Class<T> forType) {
        return (Deserializer<T>) findDeserializer(Argument.of(forType));
    }

    public <T> Serializer<? super T> findSerializer(Argument<T> forType) {
        return findSerializerProvider(forType).get();
    }

    public <T> Serializer<? super T> findSerializer(Class<T> forType) {
        return findSerializerProvider(Argument.of(forType)).get();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> Provider<Serializer<? super T>> findSerializerProvider(Argument<?> forType) {
        return (Provider) serializers.findContravariant(forType);
    }

    //endregion

    private static Argument normalizePrimitiveType(Argument t) {
        if (t != null) {
            final Argument<?> a = (Argument<?>) t;
            if (a.isPrimitive()) {
                return Argument.of(ReflectionUtils.getWrapperType(a.getType()));
            }
            return a;
        }
        return t;
    }

    private class Registry<S> {
        private final Map<TypeEntry, FactoryWrapper<S>> factories;

        Registry(Class<S> baseClass, Class<? extends BaseCodecFactory> factoryClass) {
            Collection<BaseCodecFactory> factories = new ArrayList<>(context.getBeansOfType(factoryClass));
            for (BeanDefinition<S> directDefinition : context.getBeanDefinitions(baseClass)) {
                factories.add(new ContainerSyntheticFactory<>(directDefinition, baseClass));
            }
            //noinspection Convert2MethodRef
            this.factories = factories.stream()
                    .map(factory -> new FactoryWrapper<S>(factory))
                    .collect(Collectors.toConcurrentMap(f -> new TypeEntry(f.genericType), f -> f));
        }

        public Provider<S> findContravariant(Argument<?> forType) {
            forType = normalizePrimitiveType(forType);

            Provider<S> found = null;
            Argument<?> foundType = null;
            final FactoryWrapper<S> wrapper = factories.get(new TypeEntry(forType));
            if (wrapper != null) {
                Map<String, Argument<?>> inferred = TypeInference.inferContravariant(wrapper.genericType, forType);
                if (inferred != null) {
                    return () -> wrapper.createFromInference(inferred);
                }
            } else {

                for (FactoryWrapper<S> factory : factories.values()) {
                    Map<String, Argument<?>> inferred = TypeInference.inferContravariant(factory.genericType, forType);
                    if (inferred != null) {
                        Argument<?> hereType;
                        if (inferred.isEmpty()) {
                            hereType = factory.genericType;
                        } else {
                            hereType = TypeInference.foldInferred(factory.genericType, inferred);
                        }
                        if (found != null && hereType.isAssignableFrom(foundType)) {
                            // hereType :> foundType :> type, foundType is the better choice
                            continue;
                        }

                        foundType = hereType;
                        found = () -> factory.createFromInference(inferred);
                        final TypeEntry key = new TypeEntry(forType);
                        factories.put(key, factory);
                    }
                }
            }
            if (found != null) {
                return found;
            }
            throw new IllegalStateException("Shouldn't happen, ObjectSerializer should always match. Maybe the classpath is broken?");
        }

        public Provider<S> findInvariant(Argument<?> forType) {
            for (FactoryWrapper<S> factory : factories.values()) {
                Map<String, Argument<?>> inferred = TypeInference.inferExact(factory.genericType, forType);
                if (inferred != null) {
                    return () -> factory.createFromInference(inferred);
                }
            }
            throw new NoSuchDeserializerException("No deserializer found for type " + forType.getTypeName());
        }

        final class TypeEntry {
            private final Argument<?> argument;

            TypeEntry(Argument<?> argument) {
                this.argument = argument;
            }

            @Override
            public String toString() {
                return argument.toString();
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) {
                    return true;
                }
                if (o == null || getClass() != o.getClass()) {
                    return false;
                }
                TypeEntry typeEntry = (TypeEntry) o;
                return argument.equalsType(typeEntry.argument);
            }

            @Override
            public int hashCode() {
                return argument.typeHashCode();
            }
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

        final Argument<?> genericType;

        FactoryWrapper(BaseCodecFactory factory) {
            this.factory = factory;
            this.genericType = factory.getGenericType();
            if (hasGenericPlaceholders(genericType)) {
                instances = new ConcurrentHashMap<>();
            } else {
                instances = null;
            }
        }

        private boolean hasGenericPlaceholders(Argument<?> genericType) {
            return genericType.isTypeVariable() || Arrays.stream(genericType.getTypeParameters()).anyMatch(Argument::isTypeVariable);
        }

        S createFromInference(Map<String, Argument<?>> assignment) {
            return create(assignment.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        }

        S create(Map<String, Argument<?>> assignment) {
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
        private final Argument<?> genericType;

        ContainerSyntheticFactory(BeanDefinition<? extends S> definition, Class<S> baseClass) {
            this.definition = definition;
            final List<Argument<?>> typeArguments = definition.getTypeArguments(baseClass);
            if (typeArguments.isEmpty()) {
                throw new IllegalStateException("Bean " + definition.getName() + " implements " + baseClass.getSimpleName() + " as a raw type, this is forbidden.");
            }
            this.genericType = typeArguments.iterator().next();
        }

        @Override
        public Argument<?> getGenericType() {
            return genericType;
        }

        @Override
        public S newInstance(SerdeRegistry locator, ArgumentResolver getTypeParameter) {
            return context.getBean(definition);
        }
    }

    private static class TypeVariableAssignment implements ArgumentResolver {
        static final TypeVariableAssignment EMPTY = new TypeVariableAssignment(Collections.emptyMap());

        private final Map<String, Argument<?>> assignment;
        private final int hash;

        TypeVariableAssignment(Map<String, Argument<?>> assignment) {
            this.assignment = assignment;
            int hash = 1;
            for (Map.Entry<String, Argument<?>> entry : assignment.entrySet()) {
                hash = 31 * 31 * hash + 31 * entry.getKey().hashCode() + entry.getValue().typeHashCode();
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
                    this.assignment.entrySet().stream().allMatch(entry -> entry.getValue().equalsType(((TypeVariableAssignment) o).assignment.get(entry.getKey())));
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public Argument<?> apply(String s) {
            Argument<?> type = assignment.get(s);
            if (type == null) {
                throw new IllegalStateException("Unexpected type variable " + s);
            }
            return type;
        }
    }
}
