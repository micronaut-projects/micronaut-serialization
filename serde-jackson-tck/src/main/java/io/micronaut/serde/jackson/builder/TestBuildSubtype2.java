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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder = TestBuildSubtype2.Builder.class)
public class TestBuildSubtype2 extends TestBuildSupertype2 {
    private final String bar;

    private TestBuildSubtype2(String foo, String bar) {
        super(foo);
        this.bar = bar;
    }

    public String getBar() {
        return bar;
    }

    public static class Builder {
        private String foo;
        private String bar;

        public Builder foo(String foo) {
            this.foo = foo;
            return this;
        }

        public Builder bar(String bar) {
            this.bar = bar;
            return this;
        }

        public TestBuildSubtype2 build() {
            return new TestBuildSubtype2(foo, bar);
        }
    }
}
