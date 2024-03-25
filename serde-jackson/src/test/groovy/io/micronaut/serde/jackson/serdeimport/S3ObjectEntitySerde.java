/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.serde.jackson.serdeimport;

import com.amazonaws.lambda.thirdparty.com.fasterxml.jackson.annotation.JsonCreator;
import com.amazonaws.lambda.thirdparty.com.fasterxml.jackson.annotation.JsonGetter;
import com.amazonaws.lambda.thirdparty.com.fasterxml.jackson.annotation.JsonProperty;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import io.micronaut.serde.annotation.SerdeImport;

@SerdeImport(value = S3EventNotification.S3ObjectEntity.class, mixin = S3ObjectEntitySerde.S3ObjectEntityMixin.class)
public class S3ObjectEntitySerde {
    private static final String KEY = "key";
    private static final String SIZE = "size";
    private static final String ETAG = "eTag";
    private static final String VERSION_ID = "versionId";
    private static final String SEQUENCER = "sequencer";

    static abstract class S3ObjectEntityMixin {
        @JsonGetter(KEY)
        abstract String getKey();

        @JsonGetter(SIZE)
        abstract Long getSizeAsLong();

        @JsonGetter(ETAG)
        abstract String geteTag();

        @JsonGetter(VERSION_ID)
        abstract String getVersionId();

        @JsonGetter(SEQUENCER)
        abstract String getSequencer();

        @JsonCreator
        S3ObjectEntityMixin(
            @JsonProperty(KEY) String key,
            @JsonProperty(SIZE) Long size,
            @JsonProperty(ETAG) String eTag,
            @JsonProperty(VERSION_ID) String versionId,
            @JsonProperty(SEQUENCER) String sequencer
        ) {
        }
    }
}
