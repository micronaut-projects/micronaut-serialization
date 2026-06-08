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
package io.micronaut.serde.jackson.compiletime;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.serde.annotation.SerdeableGenerated;

@SerdeableGenerated
@Introspected
public class SourceGenNonNullBoxedScalarBean {
    private Double d;
    private Integer i;
    private Boolean b;
    private String s;

    @NonNull
    public Double getD() {
        return d;
    }

    public void setD(@NonNull Double d) {
        this.d = d;
    }

    @NonNull
    public Integer getI() {
        return i;
    }

    public void setI(@NonNull Integer i) {
        this.i = i;
    }

    @NonNull
    public Boolean getB() {
        return b;
    }

    public void setB(@NonNull Boolean b) {
        this.b = b;
    }

    @NonNull
    public String getS() {
        return s;
    }

    public void setS(@NonNull String s) {
        this.s = s;
    }
}
