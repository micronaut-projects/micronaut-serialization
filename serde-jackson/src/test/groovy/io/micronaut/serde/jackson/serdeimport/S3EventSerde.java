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
import com.amazonaws.services.lambda.runtime.events.S3Event;
import com.amazonaws.services.lambda.runtime.events.models.s3.S3EventNotification;
import io.micronaut.serde.annotation.SerdeImport;

import java.util.List;

@SerdeImport(value = S3Event.class, mixin = S3EventSerde.S3EventSerdeMixin.class)
public class S3EventSerde {
    private static final String RECORDS = "Records";

    public static abstract class S3EventSerdeMixin {
        @JsonGetter(RECORDS)
        public abstract List<S3EventNotification.S3EventNotificationRecord> getRecords();

        @JsonCreator
        public S3EventSerdeMixin(@JsonProperty(RECORDS) List<S3EventNotification.S3EventNotificationRecord> records) {
        }
    }
}
