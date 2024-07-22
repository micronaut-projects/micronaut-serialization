package io.micronaut.serde.jackson.errors;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class WithNpe {

    private final String someString;

    public WithNpe(String someString) {
        this.someString = someString;
        if (!"noNPE".equals(someString)) {
            throw new NullPointerException("Simulating NPE in constructor");
        }
    }

    public String getSomeString() {
        if (true) {
            throw new NullPointerException("Simulating NPE in getter");
        }
        return someString;
    }

    @Override
    public String toString() {
        return "WithNpeToString";
    }
}
