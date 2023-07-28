package io.micronaut.serde.jackson.nested;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class NestedEntityId {

    private Integer theInt;

    private String theString;

    public Integer getTheInt() {
        return theInt;
    }

    public void setTheInt(Integer theInt) {
        this.theInt = theInt;
    }

    public String getTheString() {
        return theString;
    }

    public void setTheString(String theString) {
        this.theString = theString;
    }
}
