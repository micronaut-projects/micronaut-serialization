package io.micronaut.serde.bson;

import io.micronaut.serde.annotation.Serdeable;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonIgnore;
import org.bson.codecs.pojo.annotations.BsonProperty;

import java.util.Objects;

@Serdeable
@BsonDiscriminator
public final class Person {

    @BsonId
    private String personId;
    public String firstName;

    @BsonProperty("surname")
    public String lastName;

    @BsonIgnore
    public String password;


    @BsonProperty(useDiscriminator = true)
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
}
