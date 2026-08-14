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
import io.micronaut.serde.xml.XmlObjectMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;

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
}
