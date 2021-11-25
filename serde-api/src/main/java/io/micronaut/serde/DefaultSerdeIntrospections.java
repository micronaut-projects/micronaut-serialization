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
package io.micronaut.serde;

import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.exceptions.IntrospectionException;
import io.micronaut.core.order.OrderUtil;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.serde.annotation.SerdeImport;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.config.SerdeConfiguration;
import io.micronaut.serde.config.annotation.SerdeConfig;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Default implementation of the {@link io.micronaut.serde.SerdeIntrospections} interface
 * which looks up instrospections from {@link io.micronaut.core.beans.BeanIntrospector#SHARED}.
 *
 * @author graemerocher
 */
@Singleton
public class DefaultSerdeIntrospections implements SerdeIntrospections {

    private final Set<String> serdePackages;

    @Inject
    public DefaultSerdeIntrospections(SerdeConfiguration configuration) {
        this.serdePackages = new HashSet<>(configuration.getIncludedIntrospectionPackages());
        this.serdePackages.add("io.micronaut");
    }

    public DefaultSerdeIntrospections() {
        this.serdePackages = Collections.singleton("io.micronaut");
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
                    result = (BeanIntrospection<T>) OrderUtil.sort(candidates.stream()).findFirst().get();
                }
            }
        }
        if (result != null) {
            Class serializeType = result.getAnnotationMetadata().classValue(SerdeConfig.class, SerdeConfig.SERIALIZE_AS)
                    .orElse(null);
            if (serializeType != null && !serializeType.equals(type.getType())) {
                Argument resolved = Argument.of(
                        serializeType,
                        type.getName(),
                        type.getAnnotationMetadata(),
                        type.getTypeParameters()
                );
                return getSerializableIntrospection(resolved);
            } else {
                return result;
            }
        } else {
            throw new IntrospectionException("No serializable introspection present for type. Consider adding Serdeable.Serializable annotate to type " + type + ". Alternatively if you are not in control of the project's source code, you can use @SerdeMixin(" + type.getSimpleName() + ".class) to enable serialization of this type.");
        }
    }

    @Override
    public <T> BeanIntrospection<T> getDeserializableIntrospection(Argument<T> type) {
        final BeanIntrospection<T> introspection = getBeanIntrospector().getIntrospection(type.getType());
        if (isEnabledForDeserialization(introspection, type)) {
            Class serializeType = introspection.getAnnotationMetadata().classValue(SerdeConfig.class, SerdeConfig.DESERIALIZE_AS)
                    .orElse(null);
            if (serializeType != null) {
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
        } else {
            throw new IntrospectionException("No deserializable introspection present for type. Consider adding Serdeable.Deserializable annotate to type " + type + ". Alternatively if you are not in control of the project's source code, you can use @SerdeMixin(" + type.getSimpleName() + ".class) to enable deserialization of this type.");
        }
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
        return isEnabledForMixin(mixinsValues, type, Serdeable.Deserializable.class, "deser");
    }

    private <T extends Annotation> boolean isMixinEnabledForSerialization(List<AnnotationValue<T>> mixinsValues,
                                                                          Argument<?> type) {
        return isEnabledForMixin(mixinsValues, type, Serdeable.Serializable.class, "ser");
    }

    private <T extends Annotation> Boolean isEnabledForMixin(List<AnnotationValue<T>> mixinsValues,
                                                             Argument<?> type,
                                                             Class<? extends Annotation> ann, String member) {
        return mixinsValues.stream().filter(av -> av.classValue().orElse(Object.class).equals(type.getType()))
                .findFirst()
                .flatMap(av ->
                     av.getAnnotation(member, ann)
                             .flatMap(ser -> ser.booleanValue("enabled"))
                ).orElse(true);
    }

}
