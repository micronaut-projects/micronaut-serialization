package io.micronaut.serde.bson;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class Dog extends AbstractPet {

    private String breed;

    public String getBreed() {
        return breed;
    }

    public void setBreed(String breed) {
        this.breed = breed;
    }

}
