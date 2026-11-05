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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * CSV-aware {@link Deserializer} for bean types. Drives {@link CsvDecoder} from
 * {@link CsvIntrospection#formatFor(Class)} so column order, renames, ignores,
 * inline nested beans, trim and scalar conversion are honored.
 */
public final class CsvBeanDeserializer implements Deserializer<Object> {

    private final Class<?> type;
    private final CsvLineDecoder codec;
    private final Deserializer.DecoderContext context;

    public CsvBeanDeserializer(Class<?> type, CsvLineDecoder codec, Deserializer.DecoderContext context) {
        this.type = type;
        this.codec = codec;
        this.context = context;
    }

    @Override
    @SuppressWarnings({"unchecked", "NullAway"})
    public Object deserialize(Decoder decoder, Deserializer.DecoderContext context, Argument<? super Object> type) throws IOException {
        return deserializeNullable(decoder, context, type);
    }

    @Override
    @SuppressWarnings("unchecked")
    public @Nullable Object deserializeNullable(Decoder decoder, Deserializer.DecoderContext context, Argument<? super Object> type) throws IOException {
        BeanIntrospection<?> introspection = BeanIntrospector.SHARED.findIntrospection(this.type).orElse(null);
        if (introspection == null) {
            throw new IOException("No bean introspection for " + this.type);
        }
        CsvFormatConfig fmt = CsvIntrospection.formatFor(this.type);
        Map<String, @Nullable String> row = new LinkedHashMap<>();
        String key;
        while ((key = decoder.decodeKey()) != null) {
            row.put(key, decoder.decodeNull() ? null : decoder.decodeString());
        }
        List<String> columns = fmt.columns();
        List<String> propNames = fmt.propertyNames();
        if (columns.isEmpty()) {
            columns = new ArrayList<>(row.keySet());
            propNames = new ArrayList<>(columns);
        }
        Map<String, String> propToCol = new LinkedHashMap<>();
        for (int i = 0; i < columns.size(); i++) {
            String col = columns.get(i);
            String propName = (i < propNames.size()) ? propNames.get(i) : col;
            propToCol.put(propName, col);
        }
        List<Object> values = new ArrayList<>();
        for (BeanProperty<?, ?> bp : introspection.getBeanProperties()) {
            String propName = bp.getName();
            String col = propToCol.get(propName);
            if (col == null) {
                values.add(null);
                continue;
            }
            String cell = row.get(propName);
            CsvFormatConfig.CsvPropertyFormat pf = fmt.propertyFormats().get(propName);
            if (pf == null) {
                pf = fmt.propertyFormats().get(col);
            }
            Object value;
            if (cell == null) {
                value = null;
            } else if (pf != null && pf.config() != null) {
                value = parseInline(cell, pf.type(), pf.config());
            } else {
                value = decodeScalarProperty(propName, cell, bp.getType(), fmt);
            }
            values.add(value);
        }
        Object[] args = values.toArray();
        // Remember which properties are ignored (no CSV column) so we can reset
        // them to null after construction.
        java.util.Set<String> ignored = new java.util.LinkedHashSet<>();
        int idx = 0;
        for (BeanProperty<?, ?> bp : introspection.getBeanProperties()) {
            String propName = bp.getName();
            String col = propToCol.get(propName);
            if (col == null) {
                ignored.add(propName);
            }
            idx++;
        }
        Object instance;
        try {
            instance = introspection.instantiate(args);
        } catch (io.micronaut.core.reflect.exception.InstantiationException e) {
            // Fall back to no-arg constructor + setters for JavaBean types.
            try {
                instance = introspection.instantiate(new Object[0]);
                int i = 0;
                for (BeanProperty<?, ?> bp : introspection.getBeanProperties()) {
                    Object v = args[i];
                    if (v != null) {
                        try {
                            ((BeanProperty<Object, Object>) bp).set(instance, v);
                        } catch (Exception ignored2) {
                            // Ignore property set failures.
                        }
                    }
                    i++;
                }
            } catch (Exception e2) {
                Object[] defaults = new Object[args.length];
                int i = 0;
                for (BeanProperty<?, ?> bp : introspection.getBeanProperties()) {
                    if (args[i] == null) {
                        Class<?> t = bp.getType();
                        if (t == String.class) {
                            defaults[i] = "";
                        } else if (t == int.class || t == Integer.class) {
                            defaults[i] = 0;
                        } else if (t == long.class || t == Long.class) {
                            defaults[i] = 0L;
                        } else if (t == boolean.class || t == Boolean.class) {
                            defaults[i] = false;
                        } else if (t == double.class || t == Double.class) {
                            defaults[i] = 0.0d;
                        } else {
                            defaults[i] = null;
                        }
                    } else {
                        defaults[i] = args[i];
                    }
                    i++;
                }
                try {
                    instance = introspection.instantiate(defaults);
                } catch (Exception e3) {
                    // Last resort: no-arg + setters with defaults already applied above.
                    instance = introspection.instantiate(new Object[0]);
                    i = 0;
                    for (BeanProperty<?, ?> bp : introspection.getBeanProperties()) {
                        Object v = defaults[i];
                        if (v != null) {
                            try {
                                ((BeanProperty<Object, Object>) bp).set(instance, v);
                            } catch (Exception ignored2) {
                                // Ignore property set failures.
                            }
                        }
                        i++;
                    }
                }
            }
        }
        // Reset ignored components to null.
        for (String propName : ignored) {
            BeanProperty<?, ?> bp = introspection.getBeanProperties().stream()
                .filter(p -> p.getName().equals(propName)).findFirst().orElse(null);
            if (bp != null) {
                try {
                    ((BeanProperty<Object, Object>) bp).set(instance, null);
                } catch (Exception ignored2) {
                    // Ignore property set failures.
                }
            }
        }
        return instance;
    }

    @SuppressWarnings("unchecked")
    private Object decodeScalarProperty(String propName, String cell, Class<?> type, CsvFormatConfig fmt) throws IOException {
        String normalized = fmt.trim() ? cell.trim() : cell;
        try {
            Argument<Object> propArg = (Argument<Object>) Argument.of((Class<Object>) type);
            Deserializer<?> deserializer = context.findDeserializer(propArg).createSpecific(context, propArg);
            CsvDecoder propDecoder = CsvDecoder.forRow(Map.of(propName, normalized));
            propDecoder.setCurrentValue(normalized);
            Object result = deserializer.deserializeNullable(propDecoder, context, propArg);
            if (result != null) {
                return result;
            }
        } catch (Exception ignored) {
            // Fall through to ConversionService below.
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
        List<String> columns = fmt.columns();
        List<String> propNames = fmt.propertyNames();
        if (columns.isEmpty()) {
            columns = new ArrayList<>();
            propNames = new ArrayList<>();
            for (BeanProperty<?, ?> p : bi.getBeanProperties()) {
                columns.add(p.getName());
                propNames.add(p.getName());
            }
        }
        List<Object> values = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) {
            String propName = (i < propNames.size()) ? propNames.get(i) : columns.get(i);
            @SuppressWarnings("unchecked")
            BeanProperty<Object, Object> prop = (BeanProperty<Object, Object>) bi.getProperty(propName).orElse(null);
            if (prop == null) {
                values.add(null);
                continue;
            }
            @Nullable String cellVal = (i < parts.size()) ? parts.get(i) : null;
            if (cellVal != null && fmt.trim()) {
                cellVal = cellVal.trim();
            }
            CsvFormatConfig.CsvPropertyFormat pf = fmt.propertyFormats().get(propName);
            Object value;
            if (cellVal == null) {
                value = null;
            } else if (pf != null && pf.config() != null) {
                value = parseInline(cellVal, pf.type(), pf.config());
            } else {
                value = decodeScalarProperty(propName, cellVal, prop.getType(), fmt);
            }
            values.add(value);
        }
        // Use no-arg constructor + setters for JavaBean types; fall back to all-arg.
        try {
            Object instance = bi.instantiate(new Object[0]);
            int idx = 0;
            for (int c = 0; c < columns.size(); c++) {
                String propName = (c < propNames.size()) ? propNames.get(c) : columns.get(c);
                @SuppressWarnings("unchecked")
                BeanProperty<Object, Object> prop = (BeanProperty<Object, Object>) bi.getProperty(propName).orElse(null);
                if (prop != null && idx < values.size()) {
                    try {
                        prop.set(instance, values.get(idx));
                    } catch (Exception ignored) {
                        // Ignore property set failures.
                    }
                }
                idx++;
            }
            return instance;
        } catch (Exception e) {
            // no-arg failed; try all-arg
            try {
                return bi.instantiate(values.toArray());
            } catch (Exception e2) {
                return null;
            }
        }
    }
}
