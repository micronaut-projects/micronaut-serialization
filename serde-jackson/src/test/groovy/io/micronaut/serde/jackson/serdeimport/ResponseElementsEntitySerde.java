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

@SerdeImport(value = S3EventNotification.ResponseElementsEntity.class, mixin = ResponseElementsEntitySerde.ResponseElementsEntityMixin.class)
public class ResponseElementsEntitySerde {
    private static final String X_AMZ_ID_2 = "x-amz-id-2";
    private static final String X_AMZ_REQUEST_ID = "x-amz-request-id";

    static abstract class ResponseElementsEntityMixin {
        @JsonGetter(X_AMZ_ID_2)
        abstract String getxAmzId2();

        @JsonGetter(X_AMZ_REQUEST_ID)
        abstract String getxAmzRequestId();

        @JsonCreator
        ResponseElementsEntityMixin(@JsonProperty(X_AMZ_ID_2) String xAmzId2, @JsonProperty(X_AMZ_REQUEST_ID) String xAmzRequestId) {
        }
    }
}
