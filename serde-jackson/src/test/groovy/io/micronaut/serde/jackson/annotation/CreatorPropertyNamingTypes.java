package io.micronaut.serde.jackson.annotation;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.AnnotatedElement;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;
import jakarta.inject.Singleton;

/**
 * Types whose creator parameters are named apart from their properties, compiled with the test sources so that
 * their builders and runtime naming strategies are available.
 */
public final class CreatorPropertyNamingTypes {

    private CreatorPropertyNamingTypes() {
    }

    @Serdeable
    public static class RuntimeNamed {
        private final String firstName;
        private final String lastName;

        @JsonCreator
        public RuntimeNamed(@JsonProperty("legacy") String firstName, String lastName) {
            this.firstName = firstName;
            this.lastName = lastName;
        }

        @Serdeable.Serializable(naming = Shout.class)
        public String getFirstName() {
            return firstName;
        }

        public String getLastName() {
            return lastName;
        }
    }

    @Serdeable
    @Introspected(builder = @Introspected.IntrospectionBuilder(builderClass = BuilderNamed.Builder.class))
    public static class BuilderNamed {
        private final String firstName;

        private BuilderNamed(String firstName) {
            this.firstName = firstName;
        }

        @JsonProperty("current")
        public String getFirstName() {
            return firstName;
        }

        public static final class Builder {
            private String firstName;

            @JsonProperty("legacy")
            public Builder firstName(String firstName) {
                this.firstName = firstName;
                return this;
            }

            public BuilderNamed build() {
                return new BuilderNamed(firstName);
            }
        }
    }

    @Singleton
    public static class Shout implements PropertyNamingStrategy {
        @Override
        public String translate(AnnotatedElement element) {
            return element.getName().toUpperCase();
        }
    }
}
