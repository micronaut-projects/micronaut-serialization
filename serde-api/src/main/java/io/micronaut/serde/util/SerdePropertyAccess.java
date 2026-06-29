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
package io.micronaut.serde.util;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.BeanReadProperty;
import io.micronaut.core.beans.BeanWriteProperty;
import io.micronaut.serde.config.annotation.SerdeConfig;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;

/**
 * Utility for resolving serde property access from serde and core introspection metadata.
 */
@Internal
public final class SerdePropertyAccess {
    private static final String INTROSPECTED_PROPERTY = Introspected.Property.class.getName();
    private static final String JACKSON_PROPERTY = "com.fasterxml.jackson.annotation.JsonProperty";
    private static final String ACCESS_KIND = "accessKind";
    private static final String ACCESS = "access";
    private static final String READ_ONLY = "READ_ONLY";
    private static final String WRITE_ONLY = "WRITE_ONLY";

    private SerdePropertyAccess() {
    }

    /**
     * Determines whether the property can be serialized.
     *
     * @param beanProperty The bean read property
     * @param annotationMetadata The annotation metadata
     * @return Whether the property can be serialized
     */
    public static boolean canSerialize(BeanReadProperty<?, ?> beanProperty, AnnotationMetadata annotationMetadata) {
        return !(beanProperty instanceof BeanProperty<?, ?> property && property.isWriteOnly())
            && canSerialize(annotationMetadata);
    }

    /**
     * Determines whether the property can be serialized.
     *
     * @param annotationMetadata The annotation metadata
     * @return Whether the property can be serialized
     */
    public static boolean canSerialize(AnnotationMetadata annotationMetadata) {
        return !annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.WRITE_ONLY).orElse(false)
            && !hasJsonPropertyAccess(annotationMetadata, WRITE_ONLY)
            && hasIntrospectedPropertyAccess(annotationMetadata, Introspected.Property.Access.READ);
    }

    /**
     * Determines whether the property can be deserialized.
     *
     * @param beanProperty The bean write property
     * @param annotationMetadata The annotation metadata
     * @return Whether the property can be deserialized
     */
    public static boolean canDeserialize(BeanWriteProperty<?, ?> beanProperty, AnnotationMetadata annotationMetadata) {
        return !(beanProperty instanceof BeanProperty<?, ?> property && property.isReadOnly())
            && canDeserialize(annotationMetadata);
    }

    /**
     * Determines whether the property can be deserialized.
     *
     * @param annotationMetadata The annotation metadata
     * @return Whether the property can be deserialized
     */
    public static boolean canDeserialize(AnnotationMetadata annotationMetadata) {
        return !annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.READ_ONLY).orElse(false)
            && !hasJsonPropertyAccess(annotationMetadata, READ_ONLY)
            && hasIntrospectedPropertyAccess(annotationMetadata, Introspected.Property.Access.WRITE);
    }

    /**
     * Determines whether the property is restricted to only read or only write access.
     *
     * @param annotationMetadata The annotation metadata
     * @return Whether the property is restricted to only read or only write access
     */
    public static boolean hasRestrictedAccess(AnnotationMetadata annotationMetadata) {
        if (annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.READ_ONLY).orElse(false)
            || annotationMetadata.booleanValue(SerdeConfig.class, SerdeConfig.WRITE_ONLY).orElse(false)
            || hasJsonPropertyAccess(annotationMetadata, READ_ONLY)
            || hasJsonPropertyAccess(annotationMetadata, WRITE_ONLY)) {
            return true;
        }
        if (!annotationMetadata.hasAnnotation(INTROSPECTED_PROPERTY)) {
            return false;
        }
        String[] accessKinds = accessKindNames(annotationMetadata);
        if (accessKinds.length == 0) {
            return false;
        }
        return !Arrays.asList(accessKinds).contains(Introspected.Property.Access.READ.name())
            || !Arrays.asList(accessKinds).contains(Introspected.Property.Access.WRITE.name());
    }

    private static boolean hasJsonPropertyAccess(AnnotationMetadata annotationMetadata, String expectedAccess) {
        return annotationMetadata.getValue(JACKSON_PROPERTY, ACCESS)
            .map(value -> expectedAccess.equals(enumName(value)))
            .orElse(false);
    }

    private static String enumName(Object value) {
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        String stringValue = value.toString();
        int lastDot = stringValue.lastIndexOf('.');
        if (lastDot > -1) {
            return stringValue.substring(lastDot + 1);
        }
        return stringValue;
    }

    private static boolean hasIntrospectedPropertyAccess(AnnotationMetadata annotationMetadata,
                                                         Introspected.Property.Access access) {
        if (!annotationMetadata.hasAnnotation(INTROSPECTED_PROPERTY)) {
            return true;
        }
        String[] accessKinds = accessKindNames(annotationMetadata);
        return accessKinds.length == 0 || Arrays.asList(accessKinds).contains(access.name());
    }

    private static String[] accessKindNames(AnnotationMetadata annotationMetadata) {
        String[] accessKinds = annotationMetadata.getValue(INTROSPECTED_PROPERTY, ACCESS_KIND)
            .map(SerdePropertyAccess::toNames)
            .orElseGet(() -> new String[0]);
        if (accessKinds.length > 0) {
            return accessKinds;
        }
        accessKinds = annotationMetadata.stringValues(INTROSPECTED_PROPERTY, ACCESS_KIND);
        if (accessKinds.length > 0) {
            return accessKinds;
        }
        return Arrays.stream(annotationMetadata.enumValues(
            Introspected.Property.class,
            ACCESS_KIND,
            Introspected.Property.Access.class
        )).map(Enum::name).toArray(String[]::new);
    }

    private static String[] toNames(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(SerdePropertyAccess::enumName).toArray(String[]::new);
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            String[] names = new String[length];
            for (int i = 0; i < length; i++) {
                names[i] = enumName(Array.get(value, i));
            }
            return names;
        }
        return new String[] { enumName(value) };
    }
}
