package io.micronaut.serde.jackson.nested;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class Address {

    @JsonUnwrapped(prefix = "cd_")
    private CityData cityData = new CityData();

    private String street;

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public CityData getCityData() {
        return cityData;
    }

    public void setCityData(CityData cityData) {
        this.cityData = cityData;
    }
}
