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
package io.micronaut.serde.cbor;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.config.SerdeConfiguration;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.dataformat.cbor.CBORReadFeature;
import tools.jackson.dataformat.cbor.CBORWriteFeature;

import java.util.Collections;
import java.util.Map;

/**
 * Configuration for the CBOR streaming factory.
 *
 * @since 3.1.0
 */
@BootstrapContextCompatible
@Internal
@ConfigurationProperties(SerdeCborConfiguration.PREFIX)
public final class SerdeCborConfiguration {

    static final String PREFIX = SerdeConfiguration.PREFIX + ".cbor";

    private Map<CBORReadFeature, Boolean> cborReadFeatures = Collections.emptyMap();
    private Map<CBORWriteFeature, Boolean> cborWriteFeatures = Collections.emptyMap();
    private Map<StreamReadFeature, Boolean> streamReadFeatures = Collections.emptyMap();
    private Map<StreamWriteFeature, Boolean> streamWriteFeatures = Collections.emptyMap();
    /**
     * Defaults to {@code false}, so {@code byte[]} uses native CBOR byte strings (major type 2)
     * rather than the JSON-legacy numeric arrays.
     */
    private boolean writeBinaryAsArray;

    /**
     * Default constructor.
     */
    public SerdeCborConfiguration() {
    }

    /**
     * Returns CBOR read features.
     *
     * @return CBOR read features
     */
    public Map<CBORReadFeature, Boolean> getCborReadFeatures() {
        return cborReadFeatures;
    }

    /**
     * Sets CBOR read features.
     *
     * @param cborReadFeatures CBOR read features
     */
    public void setCborReadFeatures(Map<CBORReadFeature, Boolean> cborReadFeatures) {
        this.cborReadFeatures = cborReadFeatures;
    }

    /**
     * Returns CBOR write features.
     *
     * @return CBOR write features
     */
    public Map<CBORWriteFeature, Boolean> getCborWriteFeatures() {
        return cborWriteFeatures;
    }

    /**
     * Sets CBOR write features.
     *
     * @param cborWriteFeatures CBOR write features
     */
    public void setCborWriteFeatures(Map<CBORWriteFeature, Boolean> cborWriteFeatures) {
        this.cborWriteFeatures = cborWriteFeatures;
    }

    /**
     * Returns stream read features.
     *
     * @return Stream read features
     */
    public Map<StreamReadFeature, Boolean> getStreamReadFeatures() {
        return streamReadFeatures;
    }

    /**
     * Sets stream read features.
     *
     * @param streamReadFeatures Stream read features
     */
    public void setStreamReadFeatures(Map<StreamReadFeature, Boolean> streamReadFeatures) {
        this.streamReadFeatures = streamReadFeatures;
    }

    /**
     * Returns stream write features.
     *
     * @return Stream write features
     */
    public Map<StreamWriteFeature, Boolean> getStreamWriteFeatures() {
        return streamWriteFeatures;
    }

    /**
     * Sets stream write features.
     *
     * @param streamWriteFeatures Stream write features
     */
    public void setStreamWriteFeatures(Map<StreamWriteFeature, Boolean> streamWriteFeatures) {
        this.streamWriteFeatures = streamWriteFeatures;
    }

    /**
     * Whether {@code byte[]} values are written as numeric arrays instead of CBOR byte strings.
     *
     * <p>Unlike the global {@code micronaut.serde.write-binary-as-array} default ({@code true} for
     * JSON compatibility), CBOR defaults to {@code false} so binaries use major type 2.</p>
     *
     * @return {@code true} for numeric arrays
     */
    public boolean isWriteBinaryAsArray() {
        return writeBinaryAsArray;
    }

    /**
     * Sets whether {@code byte[]} values are written as numeric arrays.
     *
     * @param writeBinaryAsArray {@code true} for numeric arrays
     */
    public void setWriteBinaryAsArray(boolean writeBinaryAsArray) {
        this.writeBinaryAsArray = writeBinaryAsArray;
    }
}
