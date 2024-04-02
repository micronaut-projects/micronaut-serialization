/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.serde

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanIntrospectionReference
import io.micronaut.core.beans.BeanIntrospector
import io.micronaut.core.naming.NameUtils
import io.micronaut.core.reflect.InstantiationUtils
import io.micronaut.core.reflect.ReflectionUtils
import io.micronaut.core.type.Argument
import io.micronaut.serde.support.DefaultSerdeIntrospections
import jakarta.inject.Singleton

import javax.tools.JavaFileObject
import java.util.function.Predicate
import java.util.stream.Collectors

@Singleton
@Replaces(DefaultSerdeIntrospections.class)
@Requires(property = ENABLED, value = "true")
class MockSerdeIntrospections extends DefaultSerdeIntrospections {
    public static final String ENABLED = "mock.introspections.enabled"
    private final ClassLoader classLoader
    private List<BeanIntrospectionReference> references
    MockSerdeIntrospections(ApplicationContext context) {
        this.classLoader = context.getClassLoader()
        if (!isInvalidClassLoader()) {
            // horrible hack this
            def f = ReflectionUtils.getRequiredField(classLoader.getClass(), "files")
            f.setAccessible(true)
            def files = f.get(classLoader)
            def resolvedTypes = files.findAll({ JavaFileObject jfo ->
                jfo.name.contains('$Introspection')
            }).collect( { JavaFileObject jfo ->
                String className = jfo.name.substring(14, jfo.name.length() - 6).replace('/', '.')
                try {
                    classLoader.loadClass(className)
                } catch (ClassNotFoundException cnfe) {
                    return null
                }
            })


            references = resolvedTypes
                    .findAll( { it != null })
                    .collect() {
                        (BeanIntrospectionReference) InstantiationUtils.instantiate(it)
                    }
        }
    }

    @Override
    BeanIntrospector getBeanIntrospector() {
        return new BeanIntrospector() {
            @Override
            Collection<BeanIntrospection<Object>> findIntrospections(@NonNull Predicate<? super BeanIntrospectionReference<?>> filter) {
                return references.stream()
                        .filter(filter)
                        .filter(BeanIntrospectionReference::isPresent)
                        .map(BeanIntrospectionReference::load)
                        .collect(Collectors.toList()) + SHARED.findIntrospections(filter)
            }

            @Override
            Collection<Class<?>> findIntrospectedTypes(@NonNull Predicate<? super BeanIntrospectionReference<?>> filter) {
                return Collections.emptySet()
            }

            @Override
            def <T> Optional<BeanIntrospection<T>> findIntrospection(@NonNull Class<T> beanType) {
                def result = findIntrospections({ ref ->
                    ref.isPresent() && ref.beanType == beanType
                }).stream().findFirst()
                if (result.isPresent()) {
                    return result
                } else {
                    return SHARED.findIntrospection(beanType)
                }
            }
        }
    }

    @Override
    def <T> Collection<BeanIntrospection<? extends T>> findSubtypeDeserializables(@NonNull Class<T> type) {
        // horrible hack this
        if (isInvalidClassLoader()) {
            return super.findSubtypeDeserializables(type)
        }
        def f = ReflectionUtils.getRequiredField(classLoader.getClass(), "files")
        f.setAccessible(true)
        def files = f.get(classLoader)
        def resolvedTypes = files.findAll({ JavaFileObject jfo ->
            jfo.name.endsWith('$Introspection.class')
        }).collect( { JavaFileObject jfo ->
            String className = jfo.name.substring(14, jfo.name.length() - 6).replace('/', '.')
            classLoader.loadClass(className)
        })


        def list = resolvedTypes
                .collect() {
                    (BeanIntrospectionReference) InstantiationUtils.instantiate(it)
                }.findAll { it.beanType != type && type.isAssignableFrom(it.beanType) }
                .collect { it.load() }
        if (list) {
            return list
        } else {
            return super.findSubtypeDeserializables(type)
        }
    }

    @Override
    def <T> BeanIntrospection<T> getSerializableIntrospection(@NonNull Argument<T> type) {
        if (isInvalidClassLoader()) {
            return super.getSerializableIntrospection(type)
        }
        try {
            BeanIntrospection<T> i = classLoader.loadClass(NameUtils.getPackageName(type.type.name) + ".\$" + type.type.simpleName + '$Introspection')
                    .newInstance()
            return resolveIntrospectionForSerialization(type, i)
        } catch (ClassNotFoundException e) {
            return super.getSerializableIntrospection(type)
        }
    }

    private boolean isInvalidClassLoader() {
        !(classLoader.getClass().getSimpleName().equals("JavaFileObjectClassLoader"))
    }

    @Override
    def <T> BeanIntrospection<T> getDeserializableIntrospection(@NonNull Argument<T> type) {
        if (isInvalidClassLoader()) {
            return super.getDeserializableIntrospection(type)
        }
        try {
            BeanIntrospection<T> i = classLoader.loadClass(NameUtils.getPackageName(type.type.name) + ".\$" + type.type.simpleName + '$Introspection')
                    .newInstance()
            return resolveIntrospectionForDeserialization(type, i)
        } catch (ClassNotFoundException e) {
            return super.getDeserializableIntrospection(type)
        }
    }
}
