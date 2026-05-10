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
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

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
    private boolean repairingNamespaces = true;
    private boolean automaticEmptyElements;
    /**
     * Typed feature map.
     *
     * <p>Configuration keys land here as {@code Map<XmlReadFeature, Boolean>}
     * <pre>{@code
     * micronaut.serde.xml.xml-read-features.EMPTY_ELEMENT_AS_NULL: true
     * }</pre>     *
     */
    private Map<XmlReadFeature, Boolean> xmlReadFeatures = Collections.emptyMap();
    private Map<String, Boolean> xmlWriteFeatures = Collections.emptyMap();

    @Nullable
    public String getDefaultRootName() {
        return this.defaultRootName;
    }

    public void setDefaultRootName(String defaultRootName) {
        this.defaultRootName = defaultRootName;
    }

    public boolean isRepairingNamespaces() {
        return repairingNamespaces;
    }

    public void setRepairingNamespaces(boolean repairingNamespaces) {
        this.repairingNamespaces = repairingNamespaces;
    }

    public boolean isAutomaticEmptyElements() {
        return automaticEmptyElements;
    }

    public void setAutomaticEmptyElements(boolean automaticEmptyElements) {
        this.automaticEmptyElements = automaticEmptyElements;
    }

    public Map<XmlReadFeature, Boolean> getXmlReadFeatures() {
        return xmlReadFeatures;
    }

    public void setXmlReadFeatures(Map<XmlReadFeature, Boolean> xmlReadFeatures) {
        this.xmlReadFeatures = xmlReadFeatures == null ? Collections.emptyMap() : xmlReadFeatures;
    }

    public Map<String, Boolean> getXmlWriteFeatures() {
        return xmlWriteFeatures;
    }

    public void setXmlWriteFeatures(Map<String, Boolean> xmlWriteFeatures) {
        this.xmlWriteFeatures = xmlWriteFeatures == null ? Collections.emptyMap() : xmlWriteFeatures;
    }

    /**
     * Resolves the {@link #getXmlReadFeatures() xml-read-features} map into the
     * subset of {@link XmlReadFeature}s that are explicitly enabled
     * (value {@code true}).
     *
     * @return the set of enabled read features (never {@code null}); empty when
     *         no feature has been opted in.
     */
    public Set<XmlReadFeature> getEnabledReadFeatures() {
        if (xmlReadFeatures.isEmpty()) {
            return Collections.emptySet();
        }
        EnumSet<XmlReadFeature> result = EnumSet.noneOf(XmlReadFeature.class);
        for (Map.Entry<XmlReadFeature, Boolean> entry : xmlReadFeatures.entrySet()) {
            if (entry.getKey() != null && Boolean.TRUE.equals(entry.getValue())) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

}
