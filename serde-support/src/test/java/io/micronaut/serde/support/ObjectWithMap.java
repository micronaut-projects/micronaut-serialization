package io.micronaut.serde.support;

import io.micronaut.serde.annotation.Serdeable;

import java.util.Map;

@Serdeable
public class ObjectWithMap {
    private final String name;
    private final Map<String, Long> mapOfLongs;

    public ObjectWithMap(String name, Map<String, Long> mapOfLongs) {
        this.name = name;
        this.mapOfLongs = mapOfLongs;
    }

    public String getName() {
        return name;
    }

    public Map<String, Long> getMapOfLongs() {
        return mapOfLongs;
    }
}
