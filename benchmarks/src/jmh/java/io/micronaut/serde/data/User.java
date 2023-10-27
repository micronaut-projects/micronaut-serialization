package io.micronaut.serde.data;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.core.annotation.Introspected;

@Introspected
public class User {
    private Long id;
    @JsonUnwrapped
    private Name name;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Name getName() {
        return name;
    }

    public void setName(Name name) {
        this.name = name;
    }
}
