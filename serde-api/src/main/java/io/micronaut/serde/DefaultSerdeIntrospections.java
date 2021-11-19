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
import io.micronaut.serde.annotation.SerdeMixin;
import io.micronaut.serde.annotation.Serdeable;
import jakarta.inject.Singleton;

/**
 * Default implementation of the {@link io.micronaut.serde.SerdeIntrospections} interface
 * which looks up instrospections from {@link io.micronaut.core.beans.BeanIntrospector#SHARED}.
 *
 * @author graemerocher
 */
@Singleton
public class DefaultSerdeIntrospections implements SerdeIntrospections {
    @Override
    public <T> BeanIntrospection<T> getSerializableIntrospection(Argument<T> type) {
        final BeanIntrospector beanIntrospector = getBeanIntrospector();
        final Optional<BeanIntrospection<T>> introspection = beanIntrospector.findIntrospection(type.getType());
        if (introspection.isPresent()) {

            final BeanIntrospection<T> i = introspection.get();
            if (isEnabledForSerialization(i, type)) {
                return i;
            }
        }
        final Collection<BeanIntrospection<Object>> candidates =
                beanIntrospector.findIntrospections(reference -> reference.isPresent() &&
                        reference.getBeanType().isAssignableFrom(type.getType()) && isEnabledForSerialization(reference, type));
        if (CollectionUtils.isNotEmpty(candidates)) {
            if (candidates.size() == 1) {
                return (BeanIntrospection<T>) candidates.iterator().next();
            } else {
                return (BeanIntrospection<T>) OrderUtil.sort(candidates.stream()).findFirst().get();
            }
        }
        throw new IntrospectionException("No serializable introspection present for type. Consider adding Serdeable.Serializable annotate to type " + type + ". Alternatively if you are not in control of the project's source code, you can use @SerdeMixin(" + type.getSimpleName() + ".class) to enable serialization of this type.");
    }

    @Override
    public <T> BeanIntrospection<T> getDeserializableIntrospection(Argument<T> type) {
        final BeanIntrospection<T> introspection = getBeanIntrospector().getIntrospection(type.getType());
        if (isEnabledForDeserialization(introspection, type)) {
            return introspection;
        } else {
            throw new IntrospectionException("No deserializable introspection present for type. Consider adding Serdeable.Deserializable annotate to type " + type + ". Alternatively if you are not in control of the project's source code, you can use @SerdeMixin(" + type.getSimpleName() + ".class) to enable deserialization of this type.");
        }
    }

    private boolean isEnabledForDeserialization(AnnotationMetadataProvider reference, Argument<?> type) {
        final AnnotationMetadata annotationMetadata = reference.getAnnotationMetadata();
        return isCoreType(type) || (annotationMetadata.hasStereotype(Serdeable.Deserializable.class) &&
                annotationMetadata.booleanValue(Serdeable.Deserializable.class, "enabled").orElse(true)) ||
                (annotationMetadata.hasAnnotation(SerdeMixin.class) && isMixinEnabledForDeserialization(annotationMetadata.getAnnotationValuesByType(SerdeMixin.class), type));
    }

    private boolean isEnabledForSerialization(AnnotationMetadataProvider reference, Argument<?> type) {
        final AnnotationMetadata annotationMetadata = reference.getAnnotationMetadata();
        return isCoreType(type) || (annotationMetadata.hasStereotype(Serdeable.Serializable.class) &&
                annotationMetadata.booleanValue(Serdeable.Serializable.class, "enabled").orElse(true)) ||
                (annotationMetadata.hasAnnotation(SerdeMixin.class) && isMixinEnabledForSerialization(annotationMetadata.getAnnotationValuesByType(SerdeMixin.class), type));
    }

    private boolean isCoreType(Argument<?> type) {
        return type.getTypeName().startsWith("io.micronaut.");
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
