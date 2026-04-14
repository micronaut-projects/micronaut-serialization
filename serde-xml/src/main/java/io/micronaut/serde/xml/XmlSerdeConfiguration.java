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
package io.micronaut.serde.xml;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;

import java.util.Collections;
import java.util.Map;

/**
 * Configuration Properties for XML serialization/deserialization.
 *
 * @author Mousrij Hamza
 */
@ConfigurationProperties("micronaut.serde.xml")
@Internal
public final class XmlSerdeConfiguration {

    @Nullable
    private String defaultRootName;

    private Map<String, Boolean> xmlReadFeatures = Collections.emptyMap();
    private Map<String, Boolean> xmlWriteFeatures = Collections.emptyMap();

    @Nullable
    public String getDefaultRootName() {
        return this.defaultRootName;
    }

    public void setDefaultRootName(String defaultRootName) {
        this.defaultRootName = defaultRootName;
    }

    public Map<String, Boolean> getXmlReadFeatures() {
        return xmlReadFeatures;
    }

    public void setXmlReadFeatures(Map<String, Boolean> xmlReadFeatures) {
        this.xmlReadFeatures = xmlReadFeatures;
    }

    public Map<String, Boolean> getXmlWriteFeatures() {
        return xmlWriteFeatures;
    }

    public void setXmlWriteFeatures(Map<String, Boolean> xmlWriteFeatures) {
        this.xmlWriteFeatures = xmlWriteFeatures;
    }

}
