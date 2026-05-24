package io.micronaut.serde.jackson;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class UserDtoWithExplicitFirstName {
    @JsonProperty("explicit_first_name")
    private String firstName;

    private String lastName;

    public UserDtoWithExplicitFirstName() {
    }

    public UserDtoWithExplicitFirstName(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }
}
