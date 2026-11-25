package io.micronaut.serde.data.zoo;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class Dog extends Animal {
    private int barkVolume;

    public int getBarkVolume() {
        return barkVolume;
    }

    public void setBarkVolume(int barkVolume) {
        this.barkVolume = barkVolume;
    }
}
