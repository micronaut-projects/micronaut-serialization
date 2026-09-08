package io.micronaut.serde.jackson.compiletime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.micronaut.serde.annotation.SerdeableGenerated;

/**
 * An explicit property order naming a renamed property by its serialized name, with unlisted
 * properties following in declaration order.
 */
@SerdeableGenerated
@JsonPropertyOrder({"second", "first_name"})
public class SourceGenOrderedBean {
    @JsonProperty("first_name")
    private String first;
    private String second;
    private String third;
    private String fourth;

    public String getFirst() {
        return first;
    }

    public void setFirst(String first) {
        this.first = first;
    }

    public String getSecond() {
        return second;
    }

    public void setSecond(String second) {
        this.second = second;
    }

    public String getThird() {
        return third;
    }

    public void setThird(String third) {
        this.third = third;
    }

    public String getFourth() {
        return fourth;
    }

    public void setFourth(String fourth) {
        this.fourth = fourth;
    }
}
