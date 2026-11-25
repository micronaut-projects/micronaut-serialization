package io.micronaut.serde.data.zoo;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class Cat extends Animal {
    private int lives;

    public int getLives() {
        return lives;
    }

    public void setLives(int lives) {
        this.lives = lives;
    }
}
