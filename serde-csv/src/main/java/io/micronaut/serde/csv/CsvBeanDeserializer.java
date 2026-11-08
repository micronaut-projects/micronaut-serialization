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
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.csv.introspection.CsvFormatConfig;
import io.micronaut.serde.csv.introspection.CsvIntrospection;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.*;

/**
 * CSV-aware {@link Deserializer} for bean types. Drives {@link CsvDecoder} from
 * {@link CsvIntrospection#formatFor(Class)} so column order, renames, ignores,
 * inline nested beans, trim and scalar conversion are honored.
 */
public final class CsvBeanDeserializer implements Deserializer<Object> {

    private final Class<?> type;
    private final CsvLineDecoder codec;
    final Deserializer.DecoderContext context;
    private final @Nullable BeanIntrospection<?> introspection;
    private final CsvFormatConfig fmt;
    private final Map<String, String> propToCol;
    private final Map<String, BeanProperty<?, ?>> propMap;
    private final Map<String, Deserializer<?>> propDeserializers;
    private final List<BeanProperty<?, ?>> props;

    public CsvBeanDeserializer(Class<?> type, CsvLineDecoder codec, Deserializer.DecoderContext context) {
        this.type = type;
        this.codec = codec;
        this.context = context;
        BeanIntrospection<?> bi = BeanIntrospector.SHARED.findIntrospection(type).orElse(null);
        this.introspection = bi;
        CsvFormatConfig f = CsvIntrospection.formatFor(type);
        this.fmt = f;
        List<String> cols;
        List<String> pNames;
        if (bi == null) {
            cols = new ArrayList<>(f.columns());
            pNames = new ArrayList<>(f.propertyNames());
        } else {
            cols = CsvColumnResolution.resolveColumns(f, bi);
            pNames = CsvColumnResolution.resolvePropNames(f, bi, cols);
        }
        Map<String, String> ptc = new LinkedHashMap<>();
        for (int i = 0; i < cols.size(); i++) {
            String col = cols.get(i);
            String propName = (i < pNames.size()) ? pNames.get(i) : col;
            ptc.put(propName, col);
        }
        this.propToCol = ptc;
        Map<String, BeanProperty<?, ?>> pm = new LinkedHashMap<>();
        if (bi != null) {
            for (BeanProperty<?, ?> bp : bi.getBeanProperties()) {
                pm.put(bp.getName(), bp);
            }
        }
        this.propMap = pm;
        Map<String, Deserializer<?>> ds = new LinkedHashMap<>();
        if (bi != null) {
            for (BeanProperty<?, ?> bp : bi.getBeanProperties()) {
                @SuppressWarnings("unchecked")
                Argument<Object> arg = Argument.of((Class<Object>) bp.getType());
                try {
                    ds.put(bp.getName(), context.findDeserializer(arg).createSpecific(context, arg));
                } catch (Exception ignored) {
                }
            }
        }
        this.propDeserializers = ds;
        this.props = bi == null ? List.of() : List.copyOf(bi.getBeanProperties());
    }

    @Override
    public Object deserialize(Decoder decoder, Deserializer.DecoderContext context, Argument<? super Object> type) throws IOException {
        return deserializeNullable(decoder, context, type);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object deserializeNullable(Decoder decoder, Deserializer.DecoderContext context, Argument<? super Object> type) throws IOException {
        if (introspection == null) {
            throw new IOException("No bean introspection for " + this.type);
        }
        Map<String, @Nullable String> row = new LinkedHashMap<>();
        String key;
        while ((key = decoder.decodeKey()) != null) {
            row.put(key, decoder.decodeNull() ? null : decoder.decodeString());
        }
        Map<String, String> propToCol = this.propToCol;
        if (propToCol.isEmpty() && !row.isEmpty()) {
            // fall back to row keys
            propToCol = new LinkedHashMap<>();
            List<String> cols = new ArrayList<>(row.keySet());
            for (int i = 0; i < cols.size(); i++) {
                propToCol.put(cols.get(i), cols.get(i));
            }
        }
        List<Object> values = new ArrayList<>();
        List<BeanProperty<?, ?>> props = this.props;
        Set<String> ignored = new LinkedHashSet<>();
        for (BeanProperty<?, ?> bp : props) {
            String propName = bp.getName();
            String col = propToCol.get(propName);
            if (col == null) {
                values.add(null);
                ignored.add(propName);
                continue;
            }
            String cell = row.get(col);
            if (cell == null) {
                cell = row.get(propName);
            }
            CsvFormatConfig.CsvPropertyFormat pf = fmt.propertyFormats().get(propName);
            if (pf == null) {
                pf = fmt.propertyFormats().get(col);
            }
            Object value;
            if (cell == null) {
                value = null;
            } else if (pf != null) {
                value = parseInline(cell, pf.type(), pf.config());
            } else {
                value = decodeScalarProperty(propName, cell, bp.getType());
            }
            values.add(value);
        }
        Object[] args = values.toArray();
        Object instance;
        try {
            instance = introspection.instantiate(args);
        } catch (io.micronaut.core.reflect.exception.InstantiationException e) {
            try {
                instance = introspection.instantiate(new Object[0]);
                int i = 0;
                for (BeanProperty<?, ?> bp : props) {
                    Object v = args[i];
                    try {
                        ((BeanProperty<Object, Object>) bp).set(instance, v);
                    } catch (UnsupportedOperationException _ignored) {
                        // read-only property: skip
                    }
                    i++;
                }
            } catch (Exception e2) {
                Object[] defaults = new Object[args.length];
                System.arraycopy(args, 0, defaults, 0, args.length);
                try {
                    instance = introspection.instantiate(defaults);
                } catch (Exception e3) {
                    instance = introspection.instantiate(new Object[0]);
                    int i = 0;
                    for (BeanProperty<?, ?> bp : props) {
                        Object v = defaults[i];
                        try {
                            ((BeanProperty<Object, Object>) bp).set(instance, v);
                        } catch (UnsupportedOperationException _ignored) {
                            // read-only property: skip
                        }
                        i++;
                    }
                }
            }
        }
        // Reset ignored components to null.
        for (String propName : ignored) {
            BeanProperty<?, ?> bp = propMap.get(propName);
            if (bp != null) {
                try {
                    ((BeanProperty<Object, Object>) bp).set(instance, null);
                } catch (UnsupportedOperationException _ignored) {
                    // read-only property: skip
                }
            }
        }
        return instance;
    }

    @SuppressWarnings("unchecked")
    private Object decodeScalarProperty(String propName, String cell, Class<?> type) throws IOException {
        String normalized = fmt.trim() ? cell.trim() : cell;
        Deserializer<?> deserializer = propDeserializers.get(propName);
        if (deserializer != null) {
            try {
                CsvDecoder propDecoder = CsvDecoder.forRow(Map.of(propName, normalized));
                // Position the decoder at the cell value so inherited coercion applies.
                propDecoder.nextToken(); // START_OBJECT -> KEY
                propDecoder.nextToken(); // KEY -> STRING
                Object result = deserializer.deserializeNullable(propDecoder, context, Argument.of((Class<Object>) type));
                if (result != null) {
                    return result;
                }
            } catch (Exception ignored) {
            }
        }
        var converted = ConversionService.SHARED.convert(normalized, type);
        if (converted.isEmpty()) {
            throw new IOException("Cannot convert CSV value '" + normalized + "' for property '"
                + propName + "' to " + type.getName());
        }
        return converted.get();
    }

    @org.jspecify.annotations.Nullable
    @SuppressWarnings("unchecked")
    private Object parseInline(String cell, Class<?> type, CsvFormatConfig fmt) throws IOException {
        List<@Nullable String> parts = codec.decodeLine(fmt, cell);
        BeanIntrospection<?> bi = BeanIntrospector.SHARED.findIntrospection(type).orElse(null);
        if (bi == null) {
            return null;
        }
        List<String> columns = CsvColumnResolution.resolveColumns(fmt, bi);
        List<String> propNames = CsvColumnResolution.resolvePropNames(fmt, bi, columns);
        List<Object> values = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            String propName = (i < propNames.size()) ? propNames.get(i) : columns.get(i);
            @SuppressWarnings("unchecked")
            BeanProperty<Object, Object> prop = (BeanProperty<Object, Object>) bi.getProperty(propName).orElse(null);
            if (prop == null) {
                values.add(null);
                continue;
            }
            String cellVal = (i < parts.size()) ? parts.get(i) : null;
            if (cellVal != null && fmt.trim()) {
                cellVal = cellVal.trim();
            }
            CsvFormatConfig.CsvPropertyFormat pf = fmt.propertyFormats().get(propName);
            Object value;
            if (cellVal == null) {
                value = null;
            } else if (pf != null) {
                value = parseInline(cellVal, pf.type(), pf.config());
            } else {
                value = decodeScalarProperty(propName, cellVal, prop.getType());
            }
            values.add(value);
        }
        try {
            Object instance = bi.instantiate(new Object[0]);
            int idx = 0;
            for (int c = 0; c < columns.size(); c++) {
                String propName = (c < propNames.size()) ? propNames.get(c) : columns.get(c);
                @SuppressWarnings("unchecked")
                BeanProperty<Object, Object> prop = (BeanProperty<Object, Object>) bi.getProperty(propName).orElse(null);
                if (prop != null && idx < values.size()) {
                    prop.set(instance, values.get(idx));
                }
                idx++;
            }
            return instance;
        } catch (Exception e) {
            try {
                return bi.instantiate(values.toArray());
            } catch (Exception e2) {
                return null;
            }
        }
    }
}
