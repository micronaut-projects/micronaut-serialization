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
package io.micronaut.json.generated;

import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonProcessingException;

public class ObjectMappingException extends JsonProcessingException {
    protected ObjectMappingException(String msg, JsonLocation loc, Throwable rootCause) {
        super(msg, loc, rootCause);
    }

    protected ObjectMappingException(String msg) {
        super(msg);
    }

    protected ObjectMappingException(String msg, JsonLocation loc) {
        super(msg, loc);
    }

    protected ObjectMappingException(String msg, Throwable rootCause) {
        super(msg, rootCause);
    }

    protected ObjectMappingException(Throwable rootCause) {
        super(rootCause);
    }
}
