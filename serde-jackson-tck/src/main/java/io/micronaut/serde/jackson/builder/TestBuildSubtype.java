package io.micronaut.serde.jackson.builder;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonDeserialize(builder = TestBuildSubtype.Builder.class)
public class TestBuildSubtype extends TestBuildSupertype {
    private final String bar;

    private TestBuildSubtype(String foo, String bar) {
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

        public TestBuildSubtype build() {
            return new TestBuildSubtype(foo, bar);
        }
    }
}
