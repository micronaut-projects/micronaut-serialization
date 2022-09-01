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
package io.micronaut.serde.support;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.serde.SerdeIntrospections;
import io.micronaut.serde.annotation.SerdeImport;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.annotation.SerdeConfig;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Default implementation of the {@link io.micronaut.serde.SerdeIntrospections} interface
 * which looks up instrospections from {@link io.micronaut.core.beans.BeanIntrospector#SHARED}.
 *
 * @author graemerocher
 */
@Singleton
@BootstrapContextCompatible
public class DefaultSerdeIntrospections implements SerdeIntrospections {

    private final Set<String> serdePackages;

    @Inject
    public DefaultSerdeIntrospections(SerdeConfiguration configuration) {
        final List<String> introspectionPackages = configuration.getIncludedIntrospectionPackages();
        this.serdePackages = new HashSet<>(introspectionPackages);
    }

    public DefaultSerdeIntrospections() {
        this.serdePackages = Collections.singleton("io.micronaut");
    }

    @Override
    public <T> Collection<BeanIntrospection<? extends T>> findSubtypeDeserializables(Class<T> type) {
        return SerdeIntrospections.super.findSubtypeDeserializables(type);
    }

    @Override
    public <T> BeanIntrospection<T> getSerializableIntrospection(Argument<T> type) {
        final BeanIntrospector beanIntrospector = getBeanIntrospector();
        final Optional<BeanIntrospection<T>> introspection = beanIntrospector.findIntrospection(type.getType());
        BeanIntrospection<T> result = null;
        if (introspection.isPresent()) {

            final BeanIntrospection<T> i = introspection.get();
            if (isEnabledForSerialization(i, type)) {
                result = i;
            }
        }
        if (result == null) {

            final Collection<BeanIntrospection<Object>> candidates =
                    beanIntrospector.findIntrospections(reference -> reference.isPresent() &&
                            reference.getBeanType().isAssignableFrom(type.getType()) && isEnabledForSerialization(reference, type));
            if (CollectionUtils.isNotEmpty(candidates)) {
                if (candidates.size() == 1) {
                    result = (BeanIntrospection<T>) candidates.iterator().next();
                } else {
                    result = (BeanIntrospection<T>) OrderUtil.sort(candidates.stream()).findFirst().orElse(null);
                }
            }
        }
        if (result != null) {
            return resolveIntrospectionForSerialization(type, result);
        } else {
            throw new IntrospectionException("No serializable introspection present for type " + type + ". Consider adding Serdeable. Serializable annotate to type " + type + ". Alternatively if you are not in control of the project's source code, you can use @SerdeImport(" + type.getSimpleName() + ".class) to enable serialization of this type.");
        }
    }

    /**
     * Resolves an introspection for the purpose of serialization.
     * @param type The type
     * @param introspection The introspection
     * @return The resolved introspection
     * @param <T> The generic type
     */
    protected @NonNull <T> BeanIntrospection<T> resolveIntrospectionForSerialization(@NonNull  Argument<T> type, @NonNull BeanIntrospection<T> introspection) {
        final AnnotationMetadata declaredMetadata = introspection.getDeclaredMetadata();
        final AnnotationValue<SerdeConfig> serdeConfig = declaredMetadata.getDeclaredAnnotation(SerdeConfig.class);
        final Class<T> beanType = type.getType();
        Class<?> serializeType = resolveDeserAsType(
                beanType,
                serdeConfig,
                SerdeConfig.SERIALIZE_AS
        );
        if (serializeType != null && !serializeType.equals(beanType)) {
            Argument resolved = Argument.of(
                    serializeType,
                    type.getName(),
                    type.getAnnotationMetadata(),
                    type.getTypeParameters()
            );
            return getSerializableIntrospection(resolved);
        } else {
            return introspection;
        }
    }

    @Override
    public <T> BeanIntrospection<T> getDeserializableIntrospection(Argument<T> type) {
        final Class<T> rawType = type.getType();
        final BeanIntrospector beanIntrospector = getBeanIntrospector();
        final BeanIntrospection<T> introspection = beanIntrospector.findIntrospection(rawType)
                .orElseGet(() -> {
                    final Serdeable.Deserializable ann = rawType.getAnnotation(Serdeable.Deserializable.class);
                    if (ann != null) {
                        @SuppressWarnings("unchecked") final Class<T> as = (Class<T>) ann.as();
                        if (as != void.class) {
                            return beanIntrospector.getIntrospection(as);
                        }
                    }
                    // rewthrow original
                    return beanIntrospector.getIntrospection(rawType);
                });
        return resolveIntrospectionForDeserialization(type, introspection);
    }

    /**
     * Resolve an introspection.
     * @param type The type to resolve
     * @param introspection The introspection
     * @return The resolved introspection
     * @param <T> The generic type
     */
    protected @NonNull <T> BeanIntrospection<T> resolveIntrospectionForDeserialization(@NonNull Argument<T> type, @NonNull BeanIntrospection<T> introspection) {
        if (isEnabledForDeserialization(introspection, type)) {
            final AnnotationMetadata declaredMetadata = introspection.getDeclaredMetadata();
            final AnnotationValue<SerdeConfig> serdeConfig = declaredMetadata.getDeclaredAnnotation(SerdeConfig.class);
            Class<?> deserializeType = resolveDeserAsType(
                    introspection.getBeanType(),
                    serdeConfig,
                    SerdeConfig.DESERIALIZE_AS
            );
            if (deserializeType != null) {
                Argument resolved = Argument.of(
                        deserializeType,
                        type.getName(),
                        type.getAnnotationMetadata(),
                        type.getTypeParameters()
                );
                return getDeserializableIntrospection(resolved);
            } else {
                return introspection;
            }
        } else {
            throw new IntrospectionException("No deserializable introspection present for type: " + type + ". Consider adding Serdeable.Deserializable annotate to type " + type + ". Alternatively if you are not in control of the project's source code, you can use @SerdeImport(" + type.getSimpleName() + ".class) to enable deserialization of this type.");
        }
    }

    private <T> Class<?> resolveDeserAsType(
                                   Class<?> beanType,
                                   AnnotationValue<SerdeConfig> serdeConfig,
                                   String configMember) {
        Class<?> deserializeType = null;
        if (serdeConfig != null) {
            deserializeType = serdeConfig.classValue(configMember).orElse(null);
            if (deserializeType == null) {
                deserializeType = serdeConfig.stringValue(configMember).flatMap(n ->
                    ClassUtils.forName(n, beanType.getClassLoader())
                ).orElse(null);
            }
        }
        return deserializeType;
    }

    private boolean isEnabledForDeserialization(AnnotationMetadataProvider reference, Argument<?> type) {
        final AnnotationMetadata annotationMetadata = reference.getAnnotationMetadata();
        return isWithinSerdePackage(type) || (annotationMetadata.hasStereotype(Serdeable.Deserializable.class) &&
                annotationMetadata.booleanValue(Serdeable.Deserializable.class, "enabled").orElse(true)) ||
                (annotationMetadata.hasAnnotation(SerdeImport.class) && isMixinEnabledForDeserialization(annotationMetadata.getAnnotationValuesByType(SerdeImport.class), type));
    }

    private boolean isEnabledForSerialization(AnnotationMetadataProvider reference, Argument<?> type) {
        final AnnotationMetadata annotationMetadata = reference.getAnnotationMetadata();
        return isWithinSerdePackage(type) || (annotationMetadata.hasStereotype(Serdeable.Serializable.class) &&
                annotationMetadata.booleanValue(Serdeable.Serializable.class, "enabled").orElse(true)) ||
                (annotationMetadata.hasAnnotation(SerdeImport.class) && isMixinEnabledForSerialization(annotationMetadata.getAnnotationValuesByType(SerdeImport.class), type));
    }

    private boolean isWithinSerdePackage(Argument<?> type) {
        return this.serdePackages.stream()
            .anyMatch(p -> type.getTypeName().startsWith(p + "."));
    }

    private <T extends Annotation> boolean isMixinEnabledForDeserialization(List<AnnotationValue<T>> mixinsValues,
                                                                          Argument<?> type) {
        return isEnabledForMixin(mixinsValues, type, "deserializable");
    }

    private <T extends Annotation> boolean isMixinEnabledForSerialization(List<AnnotationValue<T>> mixinsValues,
                                                                          Argument<?> type) {
        return isEnabledForMixin(mixinsValues, type, "serializable");
    }

    private <T extends Annotation> Boolean isEnabledForMixin(List<AnnotationValue<T>> mixinsValues,
                                                             Argument<?> type,
                                                             String member) {
        return mixinsValues.stream().filter(av -> av.classValue().orElse(Object.class).equals(type.getType()))
                .findFirst()
                .flatMap(av ->
                     av.booleanValue(member)
                ).orElse(true);
    }

}
