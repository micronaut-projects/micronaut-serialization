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
package io.micronaut.serde.csv;

import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.csv.introspection.CsvFormatConfig;
import io.micronaut.serde.csv.introspection.CsvIntrospection;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV-aware {@link Serializer} for bean types. Drives {@link CsvEncoder} from
 * {@link CsvIntrospection#formatFor(Class)} so column order, renames, ignores,
 * inline nested beans, null values and scalar formatting are honored.
 */
public final class CsvBeanSerializer implements Serializer<Object> {

    private final Class<?> type;
    private final CsvLineEncoder codec;
    private final @Nullable BeanIntrospection<?> introspection;
    private final CsvFormatConfig fmt;
    private final List<String> columns;
    private final List<String> propNames;

    public CsvBeanSerializer(Class<?> type, CsvLineEncoder codec) {
        this.type = type;
        this.codec = codec;
        BeanIntrospection<?> bi = BeanIntrospector.SHARED.findIntrospection(type).orElse(null);
        this.introspection = bi;
        CsvFormatConfig f = CsvIntrospection.formatFor(type);
        this.fmt = f;
        if (bi == null) {
            this.columns = List.of();
            this.propNames = List.of();
        } else {
            List<String> cols = CsvColumnResolution.resolveColumns(f, bi);
            this.columns = cols;
            this.propNames = CsvColumnResolution.resolvePropNames(f, bi, cols);
        }
    }

    @Override
    public void serialize(Encoder encoder, Serializer.EncoderContext context, Argument<? extends Object> type, Object value) throws IOException {
        if (introspection == null) {
            throw new IOException("No bean introspection for " + this.type);
        }
        List<@Nullable String> cells = renderCells(value, fmt, introspection, columns, propNames);
        encoder.encodeObject(type);
        for (int i = 0; i < columns.size(); i++) {
            String col = columns.get(i);
            encoder.encodeKey(col);
            String cell = cells.get(i);
            if (cell == null) {
                encoder.encodeNull();
            } else {
                encoder.encodeString(cell);
            }
        }
        encoder.finishStructure();
    }

    private List<@Nullable String> renderCells(Object value, CsvFormatConfig fmt, BeanIntrospection<?> introspection,
                                               List<String> columns, List<String> propNames) throws IOException {
        List<@Nullable String> cells = new ArrayList<>(columns.size());
        for (int i = 0; i < columns.size(); i++) {
            String propName = (i < propNames.size()) ? propNames.get(i) : columns.get(i);
            @SuppressWarnings("unchecked")
            BeanProperty<Object, Object> prop = (BeanProperty<Object, Object>) introspection.getProperty(propName).orElse(null);
            Object v = null;
            if (prop != null) {
                try {
                    v = prop.get(value);
                } catch (UnsupportedOperationException ignored) {
                    // write-only property: skip
                }
            }
            CsvFormatConfig.CsvPropertyFormat pf = fmt.propertyFormats().get(propName);
            if (v == null) {
                cells.add(null);
            } else if (pf != null) {
                BeanIntrospection<?> bi = BeanIntrospector.SHARED.findIntrospection(v.getClass()).orElse(null);
                if (bi == null) {
                    cells.add(String.valueOf(v));
                } else {
                    List<String> nestedColumns = CsvColumnResolution.resolveColumns(pf.config(), bi);
                    cells.add(codec.encodeLine(pf.config(), renderCells(v, pf.config(), bi, nestedColumns,
                        CsvColumnResolution.resolvePropNames(pf.config(), bi, nestedColumns))));
                }
            } else {
                cells.add(String.valueOf(v));
            }
        }
        return cells;
    }
}
