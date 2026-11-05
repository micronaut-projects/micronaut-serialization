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

    public CsvBeanSerializer(Class<?> type, CsvLineEncoder codec) {
        this.type = type;
        this.codec = codec;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void serialize(Encoder encoder, Serializer.EncoderContext context, Argument<? extends Object> type, Object value) throws IOException {
        if (value == null) {
            return;
        }
        BeanIntrospection<?> introspection = BeanIntrospector.SHARED.findIntrospection(this.type).orElse(null);
        if (introspection == null) {
            throw new IOException("No bean introspection for " + this.type);
        }
        CsvFormatConfig fmt = CsvIntrospection.formatFor(this.type);
        List<String> columns = fmt.columns();
        List<String> propNames = fmt.propertyNames();
        if (columns.isEmpty()) {
            columns = new ArrayList<>();
            propNames = new ArrayList<>();
            for (BeanProperty<?, ?> p : introspection.getBeanProperties()) {
                columns.add(p.getName());
                propNames.add(p.getName());
            }
        }
        encoder.encodeObject(type);
        for (int i = 0; i < columns.size(); i++) {
            String col = columns.get(i);
            String propName = (i < propNames.size()) ? propNames.get(i) : col;
            @SuppressWarnings("unchecked")
            BeanProperty<Object, Object> prop = (BeanProperty<Object, Object>) introspection.getProperty(propName).orElse(null);
            if (prop == null) {
                continue;
            }
            Object v;
            try {
                v = prop.get(value);
            } catch (Exception e) {
                v = null;
            }
            CsvFormatConfig.CsvPropertyFormat pf = fmt.propertyFormats().get(propName);
            encoder.encodeKey(col);
            if (v == null) {
                encoder.encodeNull();
            } else if (pf != null && pf.config() != null) {
                encoder.encodeString(renderInline(v, pf.config()));
            } else {
                encoder.encodeString(String.valueOf(v));
            }
        }
        encoder.finishStructure();
    }

    private String renderInline(Object value, CsvFormatConfig fmt) throws IOException {
        Class<?> clazz = value.getClass();
        BeanIntrospection<?> bi = BeanIntrospector.SHARED.findIntrospection(clazz).orElse(null);
        if (bi == null) {
            return String.valueOf(value);
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
        List<@Nullable String> cells = new ArrayList<>(columns.size());
        for (int i = 0; i < columns.size(); i++) {
            String propName = (i < propNames.size()) ? propNames.get(i) : columns.get(i);
            @SuppressWarnings("unchecked")
            BeanProperty<Object, Object> prop = (BeanProperty<Object, Object>) bi.getProperty(propName).orElse(null);
            Object v = null;
            if (prop != null) {
                try {
                    v = prop.get(value);
                } catch (Exception ignored) {
                    v = null;
                }
            }
            CsvFormatConfig.CsvPropertyFormat pf = fmt.propertyFormats().get(propName);
            if (v == null) {
                cells.add(null);
            } else if (pf != null && pf.config() != null) {
                cells.add(renderInline(v, pf.config()));
            } else {
                cells.add(String.valueOf(v));
            }
        }
        return codec.encodeLine(fmt, cells);
    }
}
