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
package io.micronaut.serde;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
import java.util.Objects;

@Serdeable
final class ObjectWithArray {

    private List<SomeObject> vals;

    public List<SomeObject> getVals() {
        return vals;
    }

    public void setVals(List<SomeObject> vals) {
        this.vals = vals;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ObjectWithArray that = (ObjectWithArray) o;
        return Objects.equals(vals, that.vals);
    }

    @Override
    public int hashCode() {
        return Objects.hash(vals);
    }
}
