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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.serde.config.annotation.SerdeConfig;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Extra configuration placed at the argument.
 *
 * @author Denis Stepanov
 * @since 2.3.2
 */
@Internal
public final class SerdeArgumentConf {

    @Nullable
    private final String prefix;
    @Nullable
    private final String suffix;
    @Nullable
    private final String[] ignored;
    @Nullable
    private final String[] included;
    @Nullable
    private final String[] order;
    @Nullable
    private final String typeName;
    @Nullable
    private final String typeProperty;
    @Nullable
    private final SubtypeInfo subtypeInfo;

    public SerdeArgumentConf(AnnotationMetadata annotationMetadata) {
        prefix = annotationMetadata.stringValue(SerdeConfig.SerUnwrapped.class, SerdeConfig.SerUnwrapped.PREFIX).orElse(null);
        suffix = annotationMetadata.stringValue(SerdeConfig.SerUnwrapped.class, SerdeConfig.SerUnwrapped.SUFFIX).orElse(null);
        String[] ignored = null;
        String[] included = null;
        String[] order = null;
        if (annotationMetadata.isAnnotationPresent(SerdeConfig.SerIgnored.class)) {
            ignored = annotationMetadata.stringValues(SerdeConfig.SerIgnored.class);
            if (ignored.length == 0) {
                ignored = null;
            }
        }
        this.ignored = ignored;
        if (annotationMetadata.isAnnotationPresent(SerdeConfig.SerIncluded.class)) {
            included = annotationMetadata.stringValues(SerdeConfig.SerIncluded.class);
            if (included.length == 0) {
                included = null;
            }
        }
        this.included = included;
        if (annotationMetadata.isAnnotationPresent(SerdeConfig.META_ANNOTATION_PROPERTY_ORDER)) {
            order = annotationMetadata.stringValues(SerdeConfig.META_ANNOTATION_PROPERTY_ORDER);
            if (order.length == 0) {
                order = null;
            }
        }
        this.order = order;
        this.typeName = annotationMetadata.stringValue(SerdeConfig.class, SerdeConfig.TYPE_NAME).orElse(null);
        this.typeProperty = annotationMetadata.stringValue(SerdeConfig.class, SerdeConfig.TYPE_PROPERTY).orElse(null);
        this.subtypeInfo = SubtypeInfo.create(annotationMetadata);
    }

    /**
     * Apply prefix/suffix.
     *
     * @param name The name
     * @return The name with applied prefix/suffix
     */
    public String applyPrefixSuffix(String name) {
        if (prefix != null) {
            name = prefix + name;
        }
        if (suffix != null) {
            name = name + suffix;
        }
        return name;
    }

    /**
     * Extend existing argument annotation metadata to include a new prefix/suffix.
     *
     * @param argument           The argument
     * @param <Z>                The argument type
     * @return The new argument or the previous one if not changes
     */
    public <Z> Argument<Z> extendArgumentWithPrefixSuffix(Argument<Z> argument) {
        AnnotationMetadata annotationMetadata = argument.getAnnotationMetadata();
        if (annotationMetadata.isEmpty()) {
            return argument;
        }
        String extraPrefix = annotationMetadata.stringValue(SerdeConfig.SerUnwrapped.class, SerdeConfig.SerUnwrapped.PREFIX).orElse(null);
        String extraSuffix = annotationMetadata.stringValue(SerdeConfig.SerUnwrapped.class, SerdeConfig.SerUnwrapped.SUFFIX).orElse(null);
        if (prefix == null && suffix == null) {
            return argument;
        }
        if (prefix != null) {
            extraPrefix = extraPrefix == null ? prefix : prefix + extraPrefix;
        }
        if (suffix != null) {
            extraSuffix = extraSuffix == null ? suffix : extraSuffix + suffix;
        }
        MutableAnnotationMetadata mutableAnnotationMetadata = new MutableAnnotationMetadata();
        Map<CharSequence, Object> newValues = new LinkedHashMap<>();
        if (extraPrefix != null) {
            newValues.put(SerdeConfig.SerUnwrapped.PREFIX, extraPrefix);
        }
        if (extraSuffix != null) {
            newValues.put(SerdeConfig.SerUnwrapped.SUFFIX, extraSuffix);
        }
        mutableAnnotationMetadata.addDeclaredAnnotation(SerdeConfig.SerUnwrapped.class.getName(), newValues);
        return Argument.of(
            argument.getType(),
            argument.getName(),
            new AnnotationMetadataHierarchy(
                argument.getAnnotationMetadata(),
                mutableAnnotationMetadata
            ),
            argument.getTypeParameters());
    }

    /**
     * @param allowIgnoredProperties Should allow ignored properties
     * @return The predicate or null to include all
     */
    @Nullable
    public Predicate<String> resolveAllowPropertyPredicate(boolean allowIgnoredProperties) {
        Set<String> ignoreSet = ignored != null && !allowIgnoredProperties ? CollectionUtils.setOf(ignored) : null;
        Set<String> includedSet = included != null ? CollectionUtils.setOf(included) : null;
        if (ignoreSet != null || includedSet != null) {
            return propertyName -> {
                if (ignoreSet != null && ignoreSet.contains(propertyName)) {
                    return false;
                }
                if (includedSet != null && !includedSet.contains(propertyName)) {
                    return false;
                }
                return true;
            };
        }
        return null;
    }

    // Include equals for better performance

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SerdeArgumentConf that = (SerdeArgumentConf) o;
        return Objects.equals(prefix, that.prefix)
            && Objects.equals(suffix, that.suffix)
            && Arrays.equals(ignored, that.ignored)
            && Arrays.equals(included, that.included)
            && Arrays.equals(order, that.order)
            && Objects.equals(typeName, that.typeName)
            && Objects.equals(typeProperty, that.typeProperty)
            && Objects.equals(subtypeInfo, that.subtypeInfo);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(prefix, suffix, typeName, typeProperty, subtypeInfo);
        result = 31 * result + Arrays.hashCode(ignored);
        result = 31 * result + Arrays.hashCode(included);
        result = 31 * result + Arrays.hashCode(order);
        return result;
    }

    /**
     * @return The order
     */
    @Nullable
    public String[] order() {
        return order;
    }

    /**
     * @return The ignored properties
     */
    @Nullable
    public String[] getIgnored() {
        return ignored;
    }

    /**
     * @return The included properties
     */
    @Nullable
    public String[] getIncluded() {
        return included;
    }

    /**
     * @return The type name
     */
    @Nullable
    public String getTypeName() {
        return typeName;
    }

    /**
     * @return The type property
     */
    @Nullable
    public String getTypeProperty() {
        return typeProperty;
    }

    @Nullable
    public SubtypeInfo getSubtypeInfo() {
        return subtypeInfo;
    }
}
