/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.serde.jackson.builder;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonSubTypes({
    @JsonSubTypes.Type(value = TestBuildSubtype2.class, name = "sub")
})
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    defaultImpl = TestBuildSupertype2.class
)
@JsonDeserialize(builder = TestBuildSupertype2.Builder.class)
public class TestBuildSupertype2 {
    private final String foo;

    TestBuildSupertype2(String foo) {
        this.foo = foo;
    }

    public String getFoo() {
        return foo;
    }

    public static class Builder {
        private String foo;

        public Builder foo(String foo) {
            this.foo = foo;
            return this;
        }

        public TestBuildSupertype2 build() {
            return new TestBuildSupertype2(foo);
        }
    }
}
