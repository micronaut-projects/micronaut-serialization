package io.micronaut.serde.jackson.builder;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.micronaut.core.annotation.Introspected;

@JsonDeserialize(
    builder = TestBuildMe.Builder.class
)
public class TestBuildMe {
    private final String name;
    private final int age;

    private TestBuildMe(String name, int age) {
        this.name = name;
        this.age = age;
    }

    public String getName() {
        return name;
    }

    public int getAge() {
        return age;
    }

    public static final class Builder {
        private String name;
        private int age;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder age(int age) {
            this.age = age;
            return this;
        }

        public TestBuildMe build() {
            return new TestBuildMe(
                name,
                age
            );
        }
    }
}
