package io.micronaut.serde.bson;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class Cat extends AbstractPet {

    private boolean isKitten;

    public boolean isKitten() {
        return isKitten;
    }

    public void setKitten(boolean kitten) {
        isKitten = kitten;
    }
}
