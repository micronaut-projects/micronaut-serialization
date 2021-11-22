package io.micronaut.serde.bson;

import io.micronaut.serde.annotation.Serdeable;
import org.bson.codecs.pojo.annotations.BsonCreator;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;

import java.util.Objects;

@Serdeable
@BsonDiscriminator
public final class Person3 {
    private final String theId;
    private final String someFirstName;
    @BsonProperty("surname")
    private final String someLastlName;

    @BsonProperty(useDiscriminator = true)
    private final Address addr;

    @BsonCreator
    public Person3(@BsonProperty("personId") final String theId, @BsonProperty("firstName") final String someFirstName,
                   @BsonProperty("lastName") final String someLastlName, @BsonProperty("address") final Address addr) {
        this.theId = theId;
        this.someFirstName = someFirstName;
        this.someLastlName = someLastlName;
        this.addr = addr;
    }

    @BsonId
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
        Person3 person2 = (Person3) o;
        return Objects.equals(theId, person2.theId) && Objects.equals(someFirstName, person2.someFirstName) && Objects.equals(someLastlName, person2.someLastlName) && Objects.equals(addr, person2.addr);
    }

    @Override
    public int hashCode() {
        return Objects.hash(theId, someFirstName, someLastlName, addr);
    }
}
