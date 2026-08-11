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
package io.micronaut.serde.xml.tck;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.SerdeableGenerated;

final class NestedSingleArgCtors547 {

    private NestedSingleArgCtors547() {
    }

    @SerdeableGenerated
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    static class Outer547Del {
        public Inner547Del inner;
    }

    @SerdeableGenerated(skip = true)
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    static class Inner547Del {
        public final String value;

        @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
        public Inner547Del(@JsonProperty("value") String value) {
            this.value = value;
        }
    }

    @SerdeableGenerated
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    static class Outer547Props {
        public Inner547Props inner;
    }

    @SerdeableGenerated(skip = true)
    @Introspected(accessKind = Introspected.AccessKind.FIELD)
    static class Inner547Props {
        public final String value;

        public Inner547Props(@JsonProperty("value") String value) {
            this.value = value;
        }
    }
}
