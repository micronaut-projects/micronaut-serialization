package io.micronaut.serde.bson;

import io.micronaut.serde.annotation.Serdeable;

import java.util.ArrayList;
import java.util.List;

@Serdeable
public class Pets {

    private List<AbstractPet> pets = new ArrayList<>();

    public List<AbstractPet> getPets() {
        return pets;
    }

    public void setPets(List<AbstractPet> pets) {
        this.pets = pets;
    }
}
