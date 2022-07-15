package io.micronaut.serde.bson;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

import java.util.Objects;

@Serdeable
public final class Person {

    @JsonProperty("_id")
    private String personId;
    public String firstName;

    @JsonProperty("surname")
    public String lastName;

    @JsonIgnore
    public String password;

    public Address addr;

    public Person(){
    }

    public Person(String personId, String firstName, String lastName, String password, Address addr) {
        this.personId = personId;
        this.firstName = firstName;
        this.lastName = lastName;
        this.password = password;
        this.addr = addr;
    }

    public String getPersonId() {
        return personId;
    }

    public void setPersonId(String personId) {
        this.personId = personId;
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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Address getAddr() {
        return addr;
    }

    public void setAddr(Address addr) {
        this.addr = addr;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Person person = (Person) o;
        return Objects.equals(personId, person.personId) && Objects.equals(firstName, person.firstName) && Objects.equals(lastName, person.lastName) && Objects.equals(password, person.password) && Objects.equals(addr, person.addr);
    }

    @Override
    public int hashCode() {
        return Objects.hash(personId, firstName, lastName, password, addr);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Person{");
        sb.append("personId='").append(personId).append('\'');
        sb.append(", firstName='").append(firstName).append('\'');
        sb.append(", lastName='").append(lastName).append('\'');
        sb.append(", password='").append(password).append('\'');
        sb.append(", addr=").append(addr);
        sb.append('}');
        return sb.toString();
    }
}
