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
