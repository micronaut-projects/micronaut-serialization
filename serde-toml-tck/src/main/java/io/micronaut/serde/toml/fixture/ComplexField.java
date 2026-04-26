package io.micronaut.serde.toml.fixture;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Objects;

@Serdeable
@JsonPropertyOrder({"foo"})
public class ComplexField {
    private Book foo;

    public Book getFoo() {
        return foo;
    }

    public void setFoo(Book foo) {
        this.foo = foo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ComplexField that)) {
            return false;
        }
        return Objects.equals(foo, that.foo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(foo);
    }
}
