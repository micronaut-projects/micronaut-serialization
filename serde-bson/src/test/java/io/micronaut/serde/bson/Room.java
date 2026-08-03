package io.micronaut.serde.bson;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class Room {

    private String name;
    private RoomState activeState;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public RoomState getActiveState() {
        return activeState;
    }

    public void setActiveState(RoomState activeState) {
        this.activeState = activeState;
    }
}
