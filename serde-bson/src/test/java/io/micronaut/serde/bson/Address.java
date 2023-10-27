package io.micronaut.serde.bson;

import io.micronaut.serde.annotation.Serdeable;

import java.util.Objects;

@Serdeable
public class Address {

    private String address;
    private String street;
    private String town;
    private String postcode;

    public Address(String address, String street, String town, String postcode) {
        this.address = address;
        this.street = street;
        this.town = town;
        this.postcode = postcode;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getTown() {
        return town;
    }

    public void setTown(String town) {
        this.town = town;
    }

    public String getPostcode() {
        return postcode;
    }

    public void setPostcode(String postcode) {
        this.postcode = postcode;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Address address1 = (Address) o;
        return Objects.equals(address, address1.address) && Objects.equals(street, address1.street) && Objects.equals(town, address1.town) && Objects.equals(postcode, address1.postcode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, street, town, postcode);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Address{");
        sb.append("address='").append(address).append('\'');
        sb.append(", street='").append(street).append('\'');
        sb.append(", town='").append(town).append('\'');
        sb.append(", postcode='").append(postcode).append('\'');
        sb.append('}');
        return sb.toString();
    }
}
