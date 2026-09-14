package io.micronaut.serde.jackson.compiletime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.micronaut.serde.annotation.SerdeableGenerated;

/**
 * Properties ignored by name on the type, with getters still allowed.
 */
@SerdeableGenerated
@JsonIgnoreProperties(value = {"internal"}, allowGetters = true)
public class SourceGenIgnoredNamesBean {
    private String name;
    private String internal;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getInternal() {
        return internal;
    }

    public void setInternal(String internal) {
        this.internal = internal;
    }
}
