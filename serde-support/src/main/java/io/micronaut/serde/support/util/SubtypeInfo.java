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
package io.micronaut.serde.support.util;

import io.micronaut.context.annotation.DefaultImplementation;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.serde.config.annotation.SerdeConfig;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.micronaut.serde.config.annotation.SerdeConfig.SerSubtyped.DiscriminatorValueKind.CLASS_NAME;

/**
 * The subtype info.
 *
 * @param subtypes             The subtypes
 * @param discriminatorType    The discriminator type
 * @param discriminatorName    The discriminator name
 * @param defaultImpl          The default impl
 * @param discriminatorVisible The discriminator visible
 * @author Denis Stepanov
 */
@Internal
public record SubtypeInfo(
    @NonNull
    Map<Class<?>, String[]> subtypes,
    @NonNull
    SerdeConfig.SerSubtyped.DiscriminatorType discriminatorType,
    @NonNull
    String discriminatorName,
    @Nullable
    Class<?> defaultImpl,
    boolean discriminatorVisible
) {

    public static SubtypeInfo createForProperty(AnnotationMetadata annotationMetadata) {
        return create(annotationMetadata, false);
    }

    public static SubtypeInfo createForType(AnnotationMetadata annotationMetadata) {
        return create(annotationMetadata, true);
    }

    private static SubtypeInfo create(AnnotationMetadata annotationMetadata, boolean isClassDefinition) {

        if (!annotationMetadata.hasAnnotation(SerdeConfig.SerSubtyped.class)) {
            return null;
        }

        SerdeConfig.SerSubtyped.DiscriminatorType discriminatorType = annotationMetadata.enumValue(
            SerdeConfig.SerSubtyped.class,
            SerdeConfig.SerSubtyped.DISCRIMINATOR_TYPE,
            SerdeConfig.SerSubtyped.DiscriminatorType.class
        ).orElse(SerdeConfig.SerSubtyped.DiscriminatorType.PROPERTY);
        if (isClassDefinition && discriminatorType == SerdeConfig.SerSubtyped.DiscriminatorType.EXTERNAL_PROPERTY) {
            discriminatorType = SerdeConfig.SerSubtyped.DiscriminatorType.PROPERTY;
        }
        SerdeConfig.SerSubtyped.DiscriminatorValueKind discriminatorValue = annotationMetadata.enumValue(
            SerdeConfig.SerSubtyped.class,
            SerdeConfig.SerSubtyped.DISCRIMINATOR_VALUE,
            SerdeConfig.SerSubtyped.DiscriminatorValueKind.class
        ).orElse(CLASS_NAME);
        String discriminatorName = annotationMetadata.stringValue(
            SerdeConfig.SerSubtyped.class,
            SerdeConfig.SerSubtyped.DISCRIMINATOR_PROP
        ).orElse(discriminatorValue == CLASS_NAME ? "@class" : "@type");
        List<AnnotationValue<SerdeConfig.SerSubtyped.SerSubtype>> subtypesAnn = annotationMetadata.getAnnotationValuesByType(SerdeConfig.SerSubtyped.SerSubtype.class);
        Class<?> defaultType = annotationMetadata.classValue(DefaultImplementation.class).orElse(null);
        Map<Class<?>, String[]> subtypes = CollectionUtils.newHashMap(subtypesAnn.size());
        for (AnnotationValue<SerdeConfig.SerSubtyped.SerSubtype> subtype : subtypesAnn) {
            Optional<Class<?>> type = subtype.classValue();
            if (type.isPresent()) {
                Class<?> subtypeClass = type.get();
                String[] names = subtype.stringValues("names");
                if (names.length == 0) {
                    continue;
                }
                subtypes.put(subtypeClass, names);
            }
        }
        return new SubtypeInfo(
            subtypes,
            discriminatorType,
            discriminatorName,
            defaultType,
            annotationMetadata.booleanValue(SerdeConfig.SerSubtyped.class, SerdeConfig.SerSubtyped.DISCRIMINATOR_VISIBLE).orElse(false)
        );
    }

}
