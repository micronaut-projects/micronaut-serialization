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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamWriteFeature;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.core.annotation.Internal;
import io.micronaut.serde.config.SerdeConfiguration;

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
@ConfigurationProperties(JacksonConfiguration.PREFIX)
public final class JacksonConfiguration {

    static final String PREFIX = SerdeConfiguration.PREFIX + ".jackson";

    private Map<JsonReadFeature, Boolean> readFeatures = Collections.emptyMap();
    private Map<JsonWriteFeature, Boolean> writeFeatures = Collections.emptyMap();
    private Map<JsonFactory.Feature, Boolean> factoryFeatures = Collections.emptyMap();
    private Map<StreamWriteFeature, Boolean> streamFeatures = Collections.emptyMap();
    private Map<JsonParser.Feature, Boolean> parserFeatures = Collections.emptyMap();
    private Map<JsonGenerator.Feature, Boolean> generatorFeatures = Collections.emptyMap();
    private boolean prettyPrint;

    public Map<JsonReadFeature, Boolean> getReadFeatures() {
        return readFeatures;
    }

    public void setReadFeatures(Map<JsonReadFeature, Boolean> readFeatures) {
        this.readFeatures = readFeatures;
    }

    public Map<JsonWriteFeature, Boolean> getWriteFeatures() {
        return writeFeatures;
    }

    public void setWriteFeatures(Map<JsonWriteFeature, Boolean> writeFeatures) {
        this.writeFeatures = writeFeatures;
    }

    public Map<JsonFactory.Feature, Boolean> getFactoryFeatures() {
        return factoryFeatures;
    }

    public void setFactoryFeatures(Map<JsonFactory.Feature, Boolean> factoryFeatures) {
        this.factoryFeatures = factoryFeatures;
    }

    public Map<StreamWriteFeature, Boolean> getStreamFeatures() {
        return streamFeatures;
    }

    public void setStreamFeatures(Map<StreamWriteFeature, Boolean> streamFeatures) {
        this.streamFeatures = streamFeatures;
    }

    public Map<JsonParser.Feature, Boolean> getParserFeatures() {
        return parserFeatures;
    }

    public void setParserFeatures(Map<JsonParser.Feature, Boolean> parserFeatures) {
        this.parserFeatures = parserFeatures;
    }

    public Map<JsonGenerator.Feature, Boolean> getGeneratorFeatures() {
        return generatorFeatures;
    }

    public void setGeneratorFeatures(Map<JsonGenerator.Feature, Boolean> generatorFeatures) {
        this.generatorFeatures = generatorFeatures;
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }
}
