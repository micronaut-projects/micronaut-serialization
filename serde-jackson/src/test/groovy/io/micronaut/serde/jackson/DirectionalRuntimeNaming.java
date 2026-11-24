package io.micronaut.serde.jackson;

import io.micronaut.core.annotation.AnnotatedElement;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;
import jakarta.inject.Singleton;

/**
 * Directional naming strategies that are only known at runtime, because they are beans of the compiled sources.
 */
public final class DirectionalRuntimeNaming {

    private DirectionalRuntimeNaming() {
    }

    @Serdeable
    public static class PropertyNamed {
        @Serdeable.Serializable(naming = Out.class)
        @Serdeable.Deserializable(naming = In.class)
        private String firstName;

        public String getFirstName() {
            return firstName;
        }

        public void setFirstName(String firstName) {
            this.firstName = firstName;
        }
    }

    @Serdeable.Serializable(naming = Out.class)
    @Serdeable.Deserializable(naming = In.class)
    public static class TypeNamed {
        private String lastName;

        public String getLastName() {
            return lastName;
        }

        public void setLastName(String lastName) {
            this.lastName = lastName;
        }
    }

    @Singleton
    public static class Out implements PropertyNamingStrategy {
        @Override
        public String translate(AnnotatedElement element) {
            return "out_" + element.getName();
        }
    }

    @Singleton
    public static class In implements PropertyNamingStrategy {
        @Override
        public String translate(AnnotatedElement element) {
            return "in_" + element.getName();
        }
    }
}
