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
package io.micronaut.serde.csv.introspection;

import io.micronaut.core.annotation.Internal;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.csv.annotation.CsvFormat;
import io.micronaut.serde.csv.annotation.CsvProperty;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the CSV format and column layout for a bean type from the compile-time generated
 * {@link BeanIntrospection} metadata.
 *
 * <p>This is the runtime seam shared by the CSV backends: the Apache Commons CSV and Jackson
 * CSV mappers both consume the resulting {@link CsvFormatConfig}. Resolution never uses
 * reflection, so it is compatible with GraalVM native images, and the result is cached per
 * type so the serialization hot path only performs a map lookup.</p>
 * @since 3.2.5
 */
@Internal
public final class CsvIntrospection {

    private static final Map<Class<?>, CsvFormatConfig> CACHE = new ConcurrentHashMap<>();
    private static final ThreadLocal<Set<Class<?>>> RESOLVING = ThreadLocal.withInitial(HashSet::new);

    private CsvIntrospection() {
    }

    /**
     * Resolves and caches the CSV format for the given type. Types without an introspection
     * or without CSV annotations resolve to {@link CsvFormatConfig#DEFAULT}.
     *
     * @param type The bean type
     * @return The resolved format
     */
    public static CsvFormatConfig formatFor(Class<?> type) {
        CsvFormatConfig cached = CACHE.get(type);
        if (cached != null) {
            return cached;
        }
        Set<Class<?>> resolving = RESOLVING.get();
        if (!resolving.add(type)) {
            return CsvFormatConfig.DEFAULT;
        }
        try {
            CsvFormatConfig resolved = resolve(type);
            CsvFormatConfig existing = CACHE.putIfAbsent(type, resolved);
            return existing == null ? resolved : existing;
        } finally {
            resolving.remove(type);
            if (resolving.isEmpty()) {
                RESOLVING.remove();
            }
        }
    }

    /**
     * Resolves the ordered column names for the given type.
     *
     * @param type The bean type
     * @return The ordered column names, or an empty list when unknown
     */
    public static List<String> columnsFor(Class<?> type) {
        return formatFor(type).columns();
    }

    /**
     * Resolves the bean property names aligned with {@link #columnsFor(Class)}.
     *
     * @param type The bean type
     * @return The ordered property names, or an empty list when unknown
     */
    public static List<String> propertyNamesFor(Class<?> type) {
        return formatFor(type).propertyNames();
    }

    private static CsvFormatConfig resolve(Class<?> type) {
        BeanIntrospection<?> introspection = findIntrospection(type);
        if (introspection == null) {
            return CsvFormatConfig.DEFAULT;
        }
        AnnotationMetadata metadata = introspection.getAnnotationMetadata();
        AnnotationValue<CsvFormat> format = metadata.findAnnotation(CsvFormat.class).orElse(null);
        String[] configuredColumns = format == null ? new String[0] : format.stringValues("columns");
        ResolvedColumns resolved = resolveColumns(introspection, metadata, configuredColumns);
        boolean explicitColumns = configuredColumns.length > 0;
        Map<String, CsvFormatConfig.CsvPropertyFormat> propertyFormats = resolvePropertyFormats(introspection);
        if (format == null) {
            return CsvFormatConfig.DEFAULT
                .withColumns(explicitColumns, resolved.columns(), resolved.propertyNames())
                .withPropertyFormats(propertyFormats);
        }
        return new CsvFormatConfig(
            true,
            format.enumValue("header", CsvFormat.Header.class).orElse(CsvFormat.Header.INFER),
            explicitColumns,
            resolved.columns(),
            resolved.propertyNames(),
            format.charValue("delimiter").orElse(CsvFormatConfig.DEFAULT.delimiter()),
            format.charValue("quote").orElse(CsvFormatConfig.DEFAULT.quote()),
            format.charValue("escape").orElse(CsvFormatConfig.DEFAULT.escape()),
            format.stringValue("lineSeparator").orElse(CsvFormatConfig.DEFAULT.lineSeparator()),
            format.stringValue("nullValue").orElse(CsvFormatConfig.DEFAULT.nullValue()),
            format.booleanValue("skipEmptyLines").orElse(CsvFormatConfig.DEFAULT.skipEmptyLines()),
            format.booleanValue("trim").orElse(CsvFormatConfig.DEFAULT.trim()),
            format.enumValue("shape", CsvFormat.Shape.class).orElse(CsvFormat.Shape.INFER),
            propertyFormats
        );
    }

    private static Map<String, CsvFormatConfig.CsvPropertyFormat> resolvePropertyFormats(BeanIntrospection<?> introspection) {
        Map<String, CsvFormatConfig.CsvPropertyFormat> formats = new LinkedHashMap<>();
        for (BeanProperty<?, ?> property : introspection.getBeanProperties()) {
            AnnotationValue<CsvFormat> format = property.getAnnotationMetadata()
                .findAnnotation(CsvFormat.class)
                .orElse(null);
            if (format == null) {
                continue;
            }
            Class<?> propertyType = property.getType();
            CsvFormatConfig nested = formatFor(propertyType);
            formats.put(property.getName(), new CsvFormatConfig.CsvPropertyFormat(
                propertyType,
                resolveInlineFormat(format, nested)
            ));
        }
        for (BeanProperty<?, ?> property : introspection.getBeanProperties()) {
            CsvFormatConfig.CsvPropertyFormat format = formats.get(property.getName());
            if (format == null) {
                continue;
            }
            AnnotationMetadata propertyMetadata = property.getAnnotationMetadata();
            propertyMetadata.stringValue(SerdeConfig.class, SerdeConfig.PROPERTY)
                .filter(name -> !name.isEmpty())
                .ifPresent(name -> formats.putIfAbsent(name, format));
            propertyMetadata.findAnnotation(CsvProperty.class)
                .flatMap(csvProperty -> csvProperty.stringValue("name"))
                .filter(name -> !name.isEmpty())
                .ifPresent(name -> formats.putIfAbsent(name, format));
        }
        return formats;
    }

    private static CsvFormatConfig resolveInlineFormat(AnnotationValue<CsvFormat> format, CsvFormatConfig nested) {
        String[] configuredColumns = format.stringValues("columns");
        List<String> columns = configuredColumns.length > 0 ? List.of(configuredColumns) : nested.columns();
        List<String> propertyNames = configuredColumns.length > 0 ? columns : nested.propertyNames();
        return new CsvFormatConfig(
            true,
            CsvFormat.Header.ABSENT,
            false,
            columns,
            propertyNames,
            format.contains("delimiter") ? format.charValue("delimiter").orElse(nested.delimiter()) : nested.delimiter(),
            format.contains("quote") ? format.charValue("quote").orElse(nested.quote()) : nested.quote(),
            format.contains("escape") ? format.charValue("escape").orElse(nested.escape()) : nested.escape(),
            format.contains("lineSeparator") ? format.stringValue("lineSeparator").orElse(nested.lineSeparator()) : nested.lineSeparator(),
            format.contains("nullValue") ? format.stringValue("nullValue").orElse(nested.nullValue()) : nested.nullValue(),
            format.contains("skipEmptyLines") ? format.booleanValue("skipEmptyLines").orElse(nested.skipEmptyLines()) : nested.skipEmptyLines(),
            format.contains("trim") ? format.booleanValue("trim").orElse(nested.trim()) : nested.trim(),
            format.contains("shape") ? format.enumValue("shape", CsvFormat.Shape.class).orElse(nested.shape()) : nested.shape(),
            nested.propertyFormats()
        );
    }

    private static ResolvedColumns resolveColumns(BeanIntrospection<?> introspection,
                                                  AnnotationMetadata metadata,
                                                  String[] configuredColumns) {
        if (configuredColumns.length > 0) {
            List<String> columns = List.of(configuredColumns);
            return new ResolvedColumns(columns, columns);
        }
        String[] unifiedOrder = metadata.stringValues(SerdeConfig.META_ANNOTATION_PROPERTY_ORDER);
        if (unifiedOrder.length > 0) {
            List<String> columns = List.of(unifiedOrder);
            return new ResolvedColumns(columns, columns);
        }
        List<Column> columns = new ArrayList<>();
        int index = 0;
        for (BeanProperty<?, ?> property : introspection.getBeanProperties()) {
            AnnotationValue<CsvProperty> csvProperty = property.getAnnotationMetadata()
                .findAnnotation(CsvProperty.class)
                .orElse(null);
            if (csvProperty != null && csvProperty.booleanValue("ignore").orElse(false)) {
                index++;
                continue;
            }
            String propertyName = property.getAnnotationMetadata()
                .stringValue(SerdeConfig.class, SerdeConfig.PROPERTY)
                .filter(value -> !value.isEmpty())
                .orElse(property.getName());
            String name = propertyName;
            int order = -1;
            if (csvProperty != null) {
                name = csvProperty.stringValue("name").filter(value -> !value.isEmpty()).orElse(name);
                order = csvProperty.intValue("order").orElse(-1);
            }
            columns.add(new Column(name, propertyName, order, index++));
        }
        sort(columns);
        List<String> names = new ArrayList<>(columns.size());
        List<String> propertyNames = new ArrayList<>(columns.size());
        for (Column column : columns) {
            names.add(column.name());
            propertyNames.add(column.propertyName());
        }
        return new ResolvedColumns(List.copyOf(names), List.copyOf(propertyNames));
    }

    private static void sort(List<Column> columns) {
        boolean hasExplicitOrder = false;
        for (Column column : columns) {
            if (column.order() >= 0) {
                hasExplicitOrder = true;
                break;
            }
        }
        if (hasExplicitOrder) {
            columns.sort(Comparator
                .comparingInt((Column column) -> column.order() < 0 ? Integer.MAX_VALUE : column.order())
                .thenComparingInt(Column::index));
        }
    }

    private static @Nullable BeanIntrospection<?> findIntrospection(Class<?> type) {
        return BeanIntrospector.SHARED.findIntrospection(type).orElse(null);
    }

    private record Column(String name, String propertyName, int order, int index) {
    }

    private record ResolvedColumns(List<String> columns, List<String> propertyNames) {
    }
}
