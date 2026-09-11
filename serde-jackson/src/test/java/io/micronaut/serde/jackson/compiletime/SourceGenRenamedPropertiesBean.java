package io.micronaut.serde.jackson.compiletime;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.SerdeableGenerated;

/**
 * Renamed properties are written and read with the name resolved at build time.
 */
@SerdeableGenerated
public class SourceGenRenamedPropertiesBean {
    @JsonProperty("first_name")
    private String firstName;
    @JsonProperty("n")
    private int count;
    private String email;

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
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
}
