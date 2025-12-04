/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.serde.jackson;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.config.SerdeConfiguration;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.core.json.JsonReadFeature;
import tools.jackson.core.json.JsonWriteFeature;

import java.util.Collections;
import java.util.Map;

/**
 * Configuration for the Jackson.
 *
 * @author Denis Stepanov
 * @since 2.3
 */
@BootstrapContextCompatible
@Internal
@ConfigurationProperties(SerdeJacksonConfiguration.PREFIX)
public final class SerdeJacksonConfiguration {

    static final String PREFIX = SerdeConfiguration.PREFIX + ".jackson";

    // TODO: document breaking changes
    private Map<JsonReadFeature, Boolean> jsonReadFeatures = Collections.emptyMap();
    private Map<JsonWriteFeature, Boolean> jsonWriteFeatures = Collections.emptyMap();
    private Map<JsonFactory.Feature, Boolean> jsonFactoryFeatures = Collections.emptyMap();
    private Map<StreamReadFeature, Boolean> streamReadFeatures = Collections.emptyMap();
    private Map<StreamWriteFeature, Boolean> streamWriteFeatures = Collections.emptyMap();
    private boolean prettyPrint;

    public Map<JsonReadFeature, Boolean> getJsonReadFeatures() {
        return jsonReadFeatures;
    }

    public void setJsonReadFeatures(Map<JsonReadFeature, Boolean> jsonReadFeatures) {
        this.jsonReadFeatures = jsonReadFeatures;
    }

    public Map<JsonWriteFeature, Boolean> getJsonWriteFeatures() {
        return jsonWriteFeatures;
    }

    public void setJsonWriteFeatures(Map<JsonWriteFeature, Boolean> jsonWriteFeatures) {
        this.jsonWriteFeatures = jsonWriteFeatures;
    }

    public Map<JsonFactory.Feature, Boolean> getJsonFactoryFeatures() {
        return jsonFactoryFeatures;
    }

    public void setJsonFactoryFeatures(Map<JsonFactory.Feature, Boolean> jsonFactoryFeatures) {
        this.jsonFactoryFeatures = jsonFactoryFeatures;
    }

    public Map<StreamWriteFeature, Boolean> getStreamWriteFeatures() {
        return streamWriteFeatures;
    }

    public void setStreamWriteFeatures(Map<StreamWriteFeature, Boolean> streamWriteFeatures) {
        this.streamWriteFeatures = streamWriteFeatures;
    }

    public Map<StreamReadFeature, Boolean> getStreamReadFeatures() {
        return streamReadFeatures;
    }

    public void setStreamReadFeatures(Map<StreamReadFeature, Boolean> streamReadFeatures) {
        this.streamReadFeatures = streamReadFeatures;
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }
}
