package io.micronaut.serde.bson;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Creator;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Objects;

@Serdeable
public final class Person2 {
    private final String theId;
    private final String someFirstName;
    private final String someLastlName;

    private final Address addr;

    @Creator
    public Person2(@JsonProperty("personId") final String theId, @JsonProperty("firstName") final String someFirstName,
                   @JsonProperty("lastName") final String someLastlName, @JsonProperty("address") final Address addr) {
        this.theId = theId;
        this.someFirstName = someFirstName;
        this.someLastlName = someLastlName;
        this.addr = addr;
    }

    public String getTheId() {
        return theId;
    }

    public String getSomeFirstName() {
        return someFirstName;
    }

    public String getSomeLastlName() {
        return someLastlName;
    }

    public Address getAddr() {
        return addr;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Person2 person2 = (Person2) o;
        return Objects.equals(theId, person2.theId) && Objects.equals(someFirstName, person2.someFirstName) && Objects.equals(someLastlName, person2.someLastlName) && Objects.equals(addr, person2.addr);
    }

    @Override
    public int hashCode() {
        return Objects.hash(theId, someFirstName, someLastlName, addr);
    }
}
