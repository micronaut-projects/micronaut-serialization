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
package io.micronaut.serde.xml.tck;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import tools.jackson.dataformat.xml.annotation.JacksonXmlCData;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlText;

import java.util.List;

/**
 * Java fixtures used to exercise generated serdes in the shared XML TCK.
 *
 * @since 3.2
 */
public final class JacksonXmlAnnotationBeans {

    private JacksonXmlAnnotationBeans() {
    }

    /**
     * A bean with attribute and direct CDATA text properties.
     *
     * @since 3.2
     */
    @Serdeable
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    public static final class TextBean {
        /** The language attribute. */
        @JacksonXmlProperty(isAttribute = true)
        public String language = "";

        /** The direct text content. */
        @JacksonXmlText
        @JacksonXmlCData
        public String content = "";
    }

    /**
     * A bean with namespaced wrapped CDATA collection items.
     *
     * @since 3.2
     */
    @Serdeable
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    public static final class CollectionBean {
        /** The collection values. */
        @JacksonXmlElementWrapper(localName = "items", namespace = "urn:generated-wrapper")
        @JacksonXmlProperty(localName = "item")
        @JacksonXmlCData
        public List<String> values = List.of();
    }
}
