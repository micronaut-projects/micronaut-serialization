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
package io.micronaut.serde.support;

import io.micronaut.context.BeanContext;
import io.micronaut.context.BeanDependencyGraph;
import io.micronaut.context.BeanRegistration;
import io.micronaut.context.ConfigurableBeanContext;
import io.micronaut.context.WatchableBeanContext;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.env.DevelopmentActive;
import io.micronaut.context.reload.ClassChange;
import io.micronaut.context.reload.ClassChangeEvent;
import io.micronaut.context.reload.ReloadStrategy;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.support.deserializers.SerdeDeserializationPreInstantiateCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Rebuilds the serde registry, and the object mappers made from it, when what they were built from
 * changes in development mode: a serializer or deserializer definition added or removed, or a reload
 * that changes a serializable type. It exists only in development mode, so nothing of it is on the
 * path of a serialization: the registry and the mappers are recreated as beans, and the next lookup
 * or injection gets the new ones.
 *
 * <p>The registry is recreated through {@link WatchableBeanContext#recreate(Object)}, which destroys the beans
 * that received it, the object mappers among them, as the dependency graph of a development context records,
 * so that they are created again on top of the new registry. A context that does not track bean dependencies
 * recreates nothing: the registry is kept, rather than replaced under beans that would keep the old one, and
 * the change is seen after a restart.</p>
 *
 * <p>A bean that received the registry, a mapper, or any of their dependents through a provider, such as a
 * {@code BeanProvider<JsonMapper>}, is not a dependent the graph destroys with them: the provider resolves the bean
 * on each call. Such a bean may still keep what it resolved, or a mapper it derived from it with
 * {@code cloneWithConfiguration}, {@code cloneWithViewClass} or {@code createSpecific}, which hold the registry they
 * were made from; the JSON media type codec of core keeps the mapper its provider gave it, for instance. The reloader
 * therefore recreates these beans too, after the registry, so that they derive their mappers again from the new one.
 * A derived mapper is never changed or tracked: nothing of the serialization path, the clones included, knows of
 * development mode. A mapper derived by code that the graph does not see, such as a bean that looks the mapper up in
 * the context, follows only when that bean is recreated.</p>
 *
 * <p>It holds the context only, never the registry or a mapper: a bean that received the registry is
 * a dependent of it, which recreating the registry would destroy along with its watches.</p>
 *
 * @author graemerocher
 * @since 3.3.0
 */
@Internal
@Context
@DevelopmentActive
final class DevelopmentSerdeReloader {

    private static final Logger LOG = LoggerFactory.getLogger(DevelopmentSerdeReloader.class);

    private final BeanContext beanContext;

    /**
     * @param beanContext The context, watched when it can be
     */
    DevelopmentSerdeReloader(BeanContext beanContext) {
        this.beanContext = beanContext;
        if (beanContext instanceof WatchableBeanContext watchable) {
            // the first batch is what the registry was, or will be, built from: only what changes after it matters
            watchable.definitions(Serializer.class).watch(change -> {
                if (!change.initial()) {
                    rebuild("serializer definitions changed");
                }
            });
            watchable.definitions(Deserializer.class).watch(change -> {
                if (!change.initial()) {
                    rebuild("deserializer definitions changed");
                }
            });
            // the registry decides at creation whether generated deserializers stand down for the callback
            watchable.definitions(SerdeDeserializationPreInstantiateCallback.class).watch(change -> {
                if (!change.initial()) {
                    rebuild("deserialization pre-instantiate callback changed");
                }
            });
            watchable.classChanges().watch(change -> {
                if (affectsSerdes(change)) {
                    rebuild("serializable classes changed");
                }
            });
        }
    }

    /**
     * Whether a class change concerns what the registry resolved. A change that restarts the application
     * creates a new registry with the new context. One applied in place does not: the registry caches by
     * class, so a change that retires a classloader leaves it holding classes and serdes of the retired
     * generation, and one that redefines an introspected type in place changes what its serde reads.
     *
     * @param change The class change
     * @return Whether to rebuild the registry
     */
    private static boolean affectsSerdes(ClassChangeEvent change) {
        if (change.strategy() == ReloadStrategy.RESTART) {
            return false;
        }
        if (!change.retiredLoaders().isEmpty()) {
            return true;
        }
        for (ClassChange classChange : change.changes()) {
            String className = classChange.className();
            if (className.endsWith("$Introspection") || isSerializable(className, change.newLoader())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSerializable(String className, ClassLoader loader) {
        try {
            Class<?> type = Class.forName(className, false, loader);
            return type.isAnnotationPresent(Serdeable.class)
                || type.isAnnotationPresent(Serdeable.Serializable.class)
                || type.isAnnotationPresent(Serdeable.Deserializable.class)
                || type.isAnnotationPresent(Introspected.class)
                || BeanIntrospector.SHARED.findIntrospection(type).isPresent();
        } catch (ClassNotFoundException | LinkageError e) {
            // removed, or not loadable on its own: nothing the registry can have resolved from the new generation
            return false;
        }
    }

    /**
     * Recreates the serde registries the context holds, and with them the object mappers and other beans
     * that received one. Nothing is created that was not created already: a registry or a mapper nobody
     * asked for yet is built from the current definitions when it is first asked for.
     *
     * @param reason Why, for the log
     */
    private void rebuild(String reason) {
        if (!(beanContext instanceof WatchableBeanContext context)) {
            return;
        }
        Collection<BeanRegistration<SerdeRegistry>> registries = beanContext.getActiveBeanRegistrations(SerdeRegistry.class);
        if (registries.isEmpty()) {
            return;
        }
        LOG.debug("Rebuilding the serde registry: {}", reason);
        // found before anything is destroyed, while the graph still records what each of them received
        List<Object> lazyHolders = lazyHolders(context, registries);
        for (BeanRegistration<SerdeRegistry> registry : registries) {
            if (!context.recreate(registry.bean())) {
                // no dependency graph, or a registry registered at runtime: kept, and read again after a restart
                LOG.debug("Serde registry {} not rebuilt: the context cannot recreate it and its dependents", registry.getBeanDefinition());
            }
        }
        for (Object holder : lazyHolders) {
            // false for one destroyed with a holder recreated before it
            if (context.recreate(holder)) {
                LOG.debug("Recreated {}, which holds a serde registry or a mapper through a provider", holder);
            }
        }
    }

    /**
     * The singletons that received, through a provider, a registry, or a bean that recreating the registries
     * destroys, such as a mapper, and that are not destroyed with them: what they resolved, or derived from it, holds
     * the registry it was made from. A holder found is destroyed with its own dependents when it is recreated, and
     * those may hold yet another bean through a provider, so the search goes on until it finds no new holder.
     *
     * @param context The context
     * @param registries The registries about to be recreated
     * @return The holders, in the order they were found
     */
    private List<Object> lazyHolders(WatchableBeanContext context, Collection<BeanRegistration<SerdeRegistry>> registries) {
        if (!(context instanceof ConfigurableBeanContext configurable)) {
            return List.of();
        }
        BeanDependencyGraph graph = configurable.findDependencyGraph().orElse(null);
        if (graph == null) {
            return List.of();
        }
        Set<BeanDefinition<?>> destroyed = Collections.newSetFromMap(new IdentityHashMap<>());
        for (BeanRegistration<SerdeRegistry> registry : registries) {
            destroyed.add(registry.getBeanDefinition());
            destroyed.addAll(graph.transitiveDependentsOf(registry));
        }
        List<BeanRegistration<?>> singletons = new ArrayList<>(beanContext.getActiveBeanRegistrations(Object.class));
        List<Object> holders = new ArrayList<>();
        boolean found = true;
        while (found) {
            found = false;
            for (BeanRegistration<?> registration : singletons) {
                if (destroyed.contains(registration.getBeanDefinition()) || containsInstance(holders, registration.bean())) {
                    continue;
                }
                for (BeanDependencyGraph.BeanDependency dependency : graph.dependenciesOf(registration)) {
                    if (dependency.lazy() && destroyed.contains(dependency.dependency())) {
                        holders.add(registration.bean());
                        destroyed.add(registration.getBeanDefinition());
                        destroyed.addAll(graph.transitiveDependentsOf(registration));
                        found = true;
                        break;
                    }
                }
            }
        }
        return holders;
    }

    private static boolean containsInstance(List<Object> beans, Object bean) {
        for (Object candidate : beans) {
            if (candidate == bean) {
                return true;
            }
        }
        return false;
    }
}
