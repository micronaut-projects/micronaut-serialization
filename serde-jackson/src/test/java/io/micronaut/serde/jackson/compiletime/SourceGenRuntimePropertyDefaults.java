package io.micronaut.serde.jackson.compiletime;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.SerdeableGenerated;

@SerdeableGenerated
public class SourceGenRuntimePropertyDefaults {
    private String name = "default-name";
    private boolean active = true;
    private int count = 7;
    @Nullable
    private String nullableName = "default-nullable-name";
    @Nullable
    private Boolean nullableActive = Boolean.TRUE;
    public transient int nameSetCalls;
    public transient String nameSetValue;
    public transient int activeSetCalls;
    public transient boolean activeSetValue;
    public transient int countSetCalls;
    public transient int countSetValue;
    public transient int nullableNameSetCalls;
    public transient String nullableNameSetValue;
    public transient int nullableActiveSetCalls;
    public transient Boolean nullableActiveSetValue;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        nameSetCalls++;
        nameSetValue = name;
        this.name = name;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        activeSetCalls++;
        activeSetValue = active;
        this.active = active;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        countSetCalls++;
        countSetValue = count;
        this.count = count;
    }

    @Nullable
    public String getNullableName() {
        return nullableName;
    }

    public void setNullableName(@Nullable String nullableName) {
        nullableNameSetCalls++;
        nullableNameSetValue = nullableName;
        this.nullableName = nullableName;
    }

    @Nullable
    public Boolean getNullableActive() {
        return nullableActive;
    }

    public void setNullableActive(@Nullable Boolean nullableActive) {
        nullableActiveSetCalls++;
        nullableActiveSetValue = nullableActive;
        this.nullableActive = nullableActive;
    }
}
