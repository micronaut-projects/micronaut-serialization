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
package io.micronaut.serde.jaxb.tck.stax;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.jaxb.tck.AbstractJaxbTckTest;
import io.micronaut.serde.jaxb.tck.AbstractJaxbTckTest.JaxbAnnotatedConstructor;
import io.micronaut.serde.jaxb.tck.AbstractJaxbTckTest.JaxbAnnotatedRecord;
import io.micronaut.serde.jaxb.tck.AbstractJaxbTckTest.JaxbAnnotatedSimpleBean;
import io.micronaut.serde.jaxb.tck.AbstractJaxbTckTest.JaxbIdConstructor;
import io.micronaut.serde.jaxb.tck.AbstractJaxbTckTest.JaxbIdRecord;
import io.micronaut.serde.jaxb.tck.AbstractJaxbTckTest.JaxbIdRecordReferences;
import io.micronaut.serde.jaxb.tck.AbstractJaxbTckTest.JaxbIdShapeReferences;
import io.micronaut.serde.jaxb.tck.AbstractJaxbTckTest.JaxbIdSimpleBean;
import io.micronaut.serde.xml.XmlObjectMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the JAXB compatibility scenarios against the StAX XML mapper.
 *
 * @since 3.2
 */
@MicronautTest
class JaxbStaxTckTest extends AbstractJaxbTckTest {
    @Inject
    XmlObjectMapper xmlMapper;

    @Override
    protected String writeXml(Object value) throws Exception {
        return xmlMapper.writeValueAsString(value);
    }

    @Override
    protected <T> T readXml(String xml, Class<T> type) throws Exception {
        return xmlMapper.readValue(xml, Argument.of(type));
    }

    @Test
    void xmlIdReferencesWorkForRecordShape() throws Exception {
        JaxbIdRecordReferences value = new JaxbIdRecordReferences();
        value.record = new JaxbIdRecord("record", "Record");
        value.recordReference = value.record;

        JaxbIdRecordReferences decoded = readXml(writeXml(value), JaxbIdRecordReferences.class);

        assertTrue(decoded.recordReference == decoded.record);
    }

    @Test
    void xmlIdReferencesWorkForConstructorOnlyAndSimpleShapes() throws Exception {
        JaxbIdShapeReferences value = new JaxbIdShapeReferences();
        value.constructor = new JaxbIdConstructor("constructor", "Constructor");
        value.constructorReference = value.constructor;
        value.bean = new JaxbIdSimpleBean();
        value.bean.id = "bean";
        value.bean.name = "Bean";
        value.beanReference = value.bean;

        JaxbIdShapeReferences decoded = readXml(writeXml(value), JaxbIdShapeReferences.class);

        assertTrue(decoded.constructorReference == decoded.constructor);
        assertTrue(decoded.beanReference == decoded.bean);
    }

    @Test
    void jaxbAnnotationsWorkForRecordAndBeanShapes() throws Exception {
        JaxbAnnotatedRecord record = new JaxbAnnotatedRecord("record", "Record", List.of("one", "two"), "ignored");
        String recordXml = writeXml(record);
        JaxbAnnotatedRecord decodedRecord = readXml(recordXml, JaxbAnnotatedRecord.class);
        assertEquals("<recordShape code=\"record\"><title>Record</title><tags><tag>one</tag><tag>two</tag></tags></recordShape>", recordXml);
        assertEquals("record", decodedRecord.code());
        assertEquals("Record", decodedRecord.title());
        assertEquals(List.of("one", "two"), decodedRecord.tags());
        assertNull(decodedRecord.ignored());

        JaxbAnnotatedConstructor constructor = new JaxbAnnotatedConstructor("constructor", "Constructor", List.of("one", "two"), "ignored");
        String constructorXml = writeXml(constructor);
        JaxbAnnotatedConstructor decodedConstructor = readXml(constructorXml, JaxbAnnotatedConstructor.class);
        assertEquals("<constructorShape code=\"constructor\"><title>Constructor</title><tags><tag>one</tag><tag>two</tag></tags></constructorShape>", constructorXml);
        assertEquals("constructor", decodedConstructor.getCode());
        assertEquals("Constructor", decodedConstructor.getTitle());
        assertEquals(List.of("one", "two"), decodedConstructor.getTags());
        assertNull(decodedConstructor.getIgnored());

        JaxbAnnotatedSimpleBean simpleBean = new JaxbAnnotatedSimpleBean();
        simpleBean.code = "simple";
        simpleBean.title = "Simple";
        simpleBean.tags = List.of("one", "two");
        simpleBean.ignored = "ignored";
        String simpleBeanXml = writeXml(simpleBean);
        JaxbAnnotatedSimpleBean decodedSimpleBean = readXml(simpleBeanXml, JaxbAnnotatedSimpleBean.class);
        assertEquals("<simpleBeanShape code=\"simple\"><title>Simple</title><tags><tag>one</tag><tag>two</tag></tags></simpleBeanShape>", simpleBeanXml);
        assertEquals("simple", decodedSimpleBean.code);
        assertEquals("Simple", decodedSimpleBean.title);
        assertEquals(List.of("one", "two"), decodedSimpleBean.tags);
        assertNull(decodedSimpleBean.ignored);
    }
}
