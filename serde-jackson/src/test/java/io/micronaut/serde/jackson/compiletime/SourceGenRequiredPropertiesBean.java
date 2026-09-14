package io.micronaut.serde.jackson.compiletime;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.SerdeableGenerated;

/**
 * Required properties and aliases resolved at build time.
 */
@SerdeableGenerated
public class SourceGenRequiredPropertiesBean {
    @JsonProperty(required = true)
    private String name;
    @JsonProperty(required = true)
    private int count;
    @JsonAlias({"mail", "e-mail"})
    private String email;
    private String note;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
