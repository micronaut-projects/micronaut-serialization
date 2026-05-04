package io.micronaut.serde.jackson.compiletime;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.SerdeableGenerated;

@SerdeableGenerated
public class SourceGenGeneratedPropertyDefaults {
    private String name = "default-name";
    private boolean active = true;
    private int count = 7;
    @Nullable
    private String nullableName = "default-nullable-name";
    @Nullable
    private Boolean nullableActive = Boolean.TRUE;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    @Nullable
    public String getNullableName() {
        return nullableName;
    }

    public void setNullableName(@Nullable String nullableName) {
        this.nullableName = nullableName;
    }

    @Nullable
    public Boolean getNullableActive() {
        return nullableActive;
    }

    public void setNullableActive(@Nullable Boolean nullableActive) {
        this.nullableActive = nullableActive;
    }
}
