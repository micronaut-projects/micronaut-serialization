package io.micronaut.serde.jackson.builder.introspected;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@Introspected(builder = @Introspected.IntrospectionBuilder(builderClass = Address.Builder.class))
public class Address {

    private final String city;

    private final String street;

    public Address(String city, String street) {
        this.city = city;
        this.street = street;
    }

    public String getStreet() {
        return street;
    }

    public String getCity() {
        return city;
    }

    public static class Builder {

        private String city;
        private String street;

        public Builder city(String city) {
            this.city = city;
            return this;
        }

        public Builder street(String street) {
            this.street = street;
            return this;
        }

        public Address build() {
            return new Address(city, street);
        }
    }
}
