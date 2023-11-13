package io.micronaut.serde.jackson.builder;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

@JsonDeserialize(
    builder = TestBuildMe2.Builder.class
)
@JsonPOJOBuilder(buildMethodName = "create", withPrefix = "test")
public class TestBuildMe2 {
    private final String name;
    private final int age;

    private TestBuildMe2(String name, int age) {
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

        public Builder testName(String name) {
            this.name = name;
            return this;
        }

        public Builder testAge(int age) {
            this.age = age;
            return this;
        }

        public TestBuildMe2 create() {
            return new TestBuildMe2(
                name,
                age
            );
        }
    }
}
