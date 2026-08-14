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
package io.micronaut.serde.jaxb.tck.impl;

import io.micronaut.serde.jaxb.tck.AbstractJaxbTckTest;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.Marshaller;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Runs the JAXB compatibility scenarios against the Jakarta XML Binding reference implementation.
 *
 * @since 3.2
 */
class JaxbImplementationTckTest extends AbstractJaxbTckTest {
    private final ConcurrentMap<Class<?>, JAXBContext> contexts = new ConcurrentHashMap<>();

    @Override
    protected String writeXml(Object value) throws Exception {
        StringWriter writer = new StringWriter();
        Marshaller marshaller = context(value.getClass()).createMarshaller();
        marshaller.setProperty(Marshaller.JAXB_FRAGMENT, true);
        marshaller.marshal(value, writer);
        return writer.toString();
    }

    @Override
    protected <T> T readXml(String xml, Class<T> type) throws Exception {
        Object value = context(type).createUnmarshaller().unmarshal(new StringReader(xml));
        if (value instanceof JAXBElement<?> element) {
            value = element.getValue();
        }
        return type.cast(value);
    }

    private JAXBContext context(Class<?> type) {
        return contexts.computeIfAbsent(type, this::createContext);
    }

    private JAXBContext createContext(Class<?> type) {
        try {
            return JAXBContext.newInstance(type);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to create JAXB context for " + type.getName(), e);
        }
    }
}
