package io.micronaut.serde.jackson.compiletime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.micronaut.serde.annotation.SerdeableGenerated;

/**
 * The type-level unknown property policy is a build-time constant of the generated deserializer.
 */
@SerdeableGenerated
@JsonIgnoreProperties(ignoreUnknown = true)
public class SourceGenIgnoreUnknownBean {
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
