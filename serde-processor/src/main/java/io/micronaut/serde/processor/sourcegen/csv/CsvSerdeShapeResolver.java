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
package io.micronaut.serde.processor.sourcegen.csv;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.processor.sourcegen.beans.BeanSerdeShape;
import io.micronaut.serde.processor.sourcegen.beans.BeanSerdeShapeResolver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Resolves a {@link CsvSerdeShape} from compile-time {@code ClassElement} metadata.
 * Mirrors {@code io.micronaut.serde.csv.introspection.CsvIntrospection#resolveColumns}.
 *
 * <p>The {@code CsvFormat} and {@code CsvProperty} annotations are referenced by fully-qualified
 * name so this module does not need a direct dependency on {@code serde-csv}.</p>
 */
public final class CsvSerdeShapeResolver {

    private static final String CSV_FORMAT = "io.micronaut.serde.csv.annotation.CsvFormat";
    private static final String CSV_PROPERTY = "io.micronaut.serde.csv.annotation.CsvProperty";

    private static boolean hasCsvPropertyAnnotation(ClassElement element) {
        for (io.micronaut.inject.ast.PropertyElement property : BeanSerdeShapeResolver.introspectedProperties(element)) {
            if (property.getAnnotationMetadata().findAnnotation(CSV_FORMAT).isPresent()
                || property.getAnnotationMetadata().findAnnotation(CSV_PROPERTY).isPresent()) {
                return true;
            }
        }
        return false;
    }

    public Optional<CsvSerdeShape> resolve(ClassElement element) {
        // Only CSV-annotated beans get CSV-aware sourcegen; otherwise the generic bean path applies.
        // A type qualifies when it has @CsvFormat on the class, or any property carries
        // @CsvFormat/@CsvProperty (column rename, ignore, order, or inline nested bean).
        if (element.getAnnotationMetadata().findAnnotation(CSV_FORMAT).isEmpty()
            && !hasCsvPropertyAnnotation(element)) {
            return Optional.empty();
        }
        Optional<BeanSerdeShape> beanShape = new BeanSerdeShapeResolver().resolve(element);
        if (beanShape.isEmpty()) {
            return Optional.empty();
        }
        List<PropertyElement> beanProperties = BeanSerdeShapeResolver.introspectedProperties(element);
        AnnotationMetadata metadata = element.getAnnotationMetadata();
        AnnotationValue<?> format = metadata.findAnnotation(CSV_FORMAT).orElse(null);
        String[] configuredColumns = format == null ? new String[0] : format.stringValues("columns");

        ResolvedColumns resolved = resolveColumns(element, beanProperties, metadata, configuredColumns);

        Map<String, String> propertyToColumn = new LinkedHashMap<>();
        Map<String, Boolean> inlineProperties = new HashMap<>();
        List<String> propertyNames = resolved.propertyNames;
        for (int i = 0; i < resolved.columns.size(); i++) {
            String col = resolved.columns.get(i);
            String propName = i < propertyNames.size() ? propertyNames.get(i) : col;
            propertyToColumn.put(propName, col);
        }
        for (PropertyElement property : beanProperties) {
            property.getAnnotationMetadata()
                .findAnnotation(CSV_FORMAT).ifPresent(inline -> inlineProperties.put(property.getName(), true));
        }
        return Optional.of(new CsvSerdeShape(
            beanShape.get(),
            List.copyOf(resolved.columns),
            Map.copyOf(propertyToColumn),
            Map.copyOf(inlineProperties)
        ));
    }

    private static ResolvedColumns resolveColumns(ClassElement element,
                                                  List<PropertyElement> beanProperties,
                                                  AnnotationMetadata metadata,
                                                  String[] configuredColumns) {
        if (configuredColumns.length > 0) {
            List<String> cols = List.of(configuredColumns);
            return new ResolvedColumns(cols, cols);
        }
        List<Column> columns = new ArrayList<>();
        int index = 0;
        for (PropertyElement property : beanProperties) {
            AnnotationValue<?> csvProperty = property.getAnnotationMetadata()
                .findAnnotation(CSV_PROPERTY)
                .orElse(null);
            if (csvProperty != null && csvProperty.booleanValue("ignore").orElse(false)) {
                index++;
                continue;
            }
            String originalName = property.getName();
            String name = property.stringValue(SerdeConfig.class, SerdeConfig.PROPERTY)
                .filter(value1 -> !value1.isEmpty())
                .orElse(originalName);
            int order = -1;
            if (csvProperty != null) {
                name = csvProperty.stringValue("name").filter(value -> !value.isEmpty()).orElse(name);
                order = csvProperty.intValue("order").orElse(-1);
            }
            columns.add(new Column(name, originalName, originalName, order, index++));
        }
        String[] unifiedOrder = metadata.stringValues(SerdeConfig.class, SerdeConfig.META_ANNOTATION_PROPERTY_ORDER);
        if (unifiedOrder.length > 0) {
            sortByPropertyOrder(columns, unifiedOrder);
        } else {
            sort(columns);
        }
        List<String> names = new ArrayList<>(columns.size());
        List<String> propNames = new ArrayList<>(columns.size());
        for (Column column : columns) {
            names.add(column.name);
            propNames.add(column.propertyName);
        }
        return new ResolvedColumns(List.copyOf(names), List.copyOf(propNames));
    }

    private static void sortByPropertyOrder(List<Column> columns, String[] order) {
        Set<String> serializedNames = new HashSet<>(columns.size());
        for (Column column : columns) {
            serializedNames.add(column.propertyName);
        }
        columns.sort(Comparator
            .comparingInt((Column column) -> propertyOrderIndex(order, column, serializedNames))
            .thenComparingInt(column -> column.index));
    }

    private static int propertyOrderIndex(String[] order, Column column, Set<String> serializedNames) {
        for (int i = 0; i < order.length; i++) {
            String entry = order[i];
            if (column.propertyName.equals(entry)
                || (column.originalName.equals(entry) && !serializedNames.contains(entry))) {
                return i;
            }
        }
        return Integer.MAX_VALUE;
    }

    private static void sort(List<Column> columns) {
        boolean hasExplicitOrder = false;
        for (Column column : columns) {
            if (column.order >= 0) {
                hasExplicitOrder = true;
                break;
            }
        }
        if (hasExplicitOrder) {
            columns.sort(Comparator
                .comparingInt((Column column) -> column.order < 0 ? Integer.MAX_VALUE : column.order)
                .thenComparingInt(column -> column.index));
        }
    }

    private record ResolvedColumns(List<String> columns, List<String> propertyNames) {
    }

    private record Column(String name, String propertyName, String originalName, int order,
                          int index) {
    }
}
