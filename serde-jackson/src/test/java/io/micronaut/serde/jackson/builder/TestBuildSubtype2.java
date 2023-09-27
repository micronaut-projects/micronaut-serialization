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
