package io.micronaut.serde.jackson.builder;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonSubTypes({
    @JsonSubTypes.Type(value = TestBuildSubtype.class, name = "sub")
})
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.WRAPPER_OBJECT
)
public abstract class TestBuildSupertype {
    private final String foo;

    TestBuildSupertype(String foo) {
        this.foo = foo;
    }

    public String getFoo() {
        return foo;
    }
}
