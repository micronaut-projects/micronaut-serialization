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
import io.micronaut.serde.config.SerdeConfiguration;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.Map;

/**
 * XML-specific configuration.
 *
 * @author Mousrij Hamza
 * @since 3.1.0
 */
@Internal
@ConfigurationProperties(XmlSerdeConfiguration.PREFIX)
public final class XmlSerdeConfiguration {
    static final String PREFIX = SerdeConfiguration.PREFIX + ".format.xml";

    private boolean repairingNamespaces = true;
    private boolean automaticEmptyElements;
    private Map<XmlReadFeature, Boolean> xmlReadFeatures = Collections.emptyMap();

    /**
     * Returns whether namespace repairing is enabled.
     *
     * @return Whether namespace repairing is enabled for XML output
     */
    public boolean isRepairingNamespaces() {
        return repairingNamespaces;
    }

    /**
     * Sets whether namespace repairing is enabled.
     *
     * @param repairingNamespaces Whether namespace repairing is enabled for XML output
     */
    public void setRepairingNamespaces(boolean repairingNamespaces) {
        this.repairingNamespaces = repairingNamespaces;
    }

    /**
     * Returns whether the XML writer may use self-closing empty elements.
     *
     * @return Whether the XML writer may use self-closing empty elements
     */
    public boolean isAutomaticEmptyElements() {
        return automaticEmptyElements;
    }

    /**
     * Sets whether the XML writer may use self-closing empty elements.
     *
     * @param automaticEmptyElements Whether the XML writer may use self-closing empty elements
     */
    public void setAutomaticEmptyElements(boolean automaticEmptyElements) {
        this.automaticEmptyElements = automaticEmptyElements;
    }

    /**
     * Returns the XML read feature map.
     *
     * @return The XML read feature map
     */
    public Map<XmlReadFeature, Boolean> getXmlReadFeatures() {
        return xmlReadFeatures;
    }

    /**
     * Sets the XML read feature map.
     *
     * @param xmlReadFeatures The XML read feature map
     */
    public void setXmlReadFeatures(@Nullable Map<XmlReadFeature, Boolean> xmlReadFeatures) {
        this.xmlReadFeatures = xmlReadFeatures == null ? Collections.emptyMap() : xmlReadFeatures;
    }

    /**
     * Returns whether the given XML read feature is enabled.
     *
     * @param feature The XML read feature
     * @return Whether the feature is enabled
     */
    public boolean isReadFeatureEnabled(XmlReadFeature feature) {
        return Boolean.TRUE.equals(xmlReadFeatures.get(feature));
    }

    /**
     * Toggleable read-side features for the XML object mapper.
     *
     * <p>All features default to {@code disabled}. Enable a feature via the
     * {@code micronaut.serde.format.xml.xml-read-features.<NAME>: true} Micronaut
     * configuration property.</p>
     */
    public enum XmlReadFeature {

        /**
         * When enabled, an empty XML element with no content is decoded as {@code null} instead of an empty string or
         * empty bean.
         *
         * <p>Default ({@code disabled}) matches Jackson 3.x default behaviour: empty elements decode to {@code ""} for
         * scalar fields and to an empty bean (constructor invoked with defaults) for object-typed fields.</p>
         */
        EMPTY_ELEMENT_AS_NULL
    }

}
