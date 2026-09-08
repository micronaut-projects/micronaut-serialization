package io.micronaut.serde.jackson.compiletime;

import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import io.micronaut.serde.annotation.SerdeableGenerated;

/**
 * Only the included properties take part; everything else is ignored.
 */
@SerdeableGenerated
@JsonIncludeProperties({"name"})
public class SourceGenIncludedPropertiesBean {
    private String name;
    private String extra;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getExtra() {
        return extra;
    }

    public void setExtra(String extra) {
        this.extra = extra;
    }
}
