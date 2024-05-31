package io.micronaut.serde.support;

import io.micronaut.serde.annotation.Serdeable;

import java.util.Map;

@Serdeable
public class ObjectWithMap {
    private final String name;
    private final Map<String, Long> stringLongMap;
    private final Map<Integer, Long> integerLongMap;

    public ObjectWithMap(String name, Map<String, Long> stringLongMap, Map<Integer, Long> integerLongMap) {
        this.name = name;
        this.stringLongMap = stringLongMap;
        this.integerLongMap = integerLongMap;
    }

    public String getName() {
        return name;
    }

    public Map<String, Long> getStringLongMap() {
        return stringLongMap;
    }

    public Map<Integer, Long> getIntegerLongMap() {
        return integerLongMap;
    }
}
