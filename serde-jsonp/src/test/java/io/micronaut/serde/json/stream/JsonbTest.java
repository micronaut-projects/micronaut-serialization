/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package io.micronaut.serde.json.stream;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.json.bind.annotation.JsonbCreator;
import jakarta.json.bind.annotation.JsonbDateFormat;
import jakarta.json.bind.annotation.JsonbNumberFormat;
import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.annotation.JsonbPropertyOrder;
import jakarta.json.bind.annotation.JsonbTransient;
import java.time.Instant;

/**
 *
 * @author gkrocher
 */
@JsonbPropertyOrder({"name", "price", "dateCreated"}) 
@Serdeable
public class JsonbTest {

    @JsonbProperty("n")
    private final String name;
    @JsonbDateFormat("ddMMyy")
    private Instant dateCreated = Instant.now();
    @JsonbNumberFormat("$##.##")
    private double price = 1.1;
    @JsonbTransient
    private boolean ignored;

    @JsonbCreator
    public JsonbTest(String name) {
        this.name = name;
    }

    public Instant getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(Instant dateCreated) {
        this.dateCreated = dateCreated;
    }

    public String getName() {
        return name;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public boolean isIgnored() {
        return ignored;
    }

    public void setIgnored(boolean ignored) {
        this.ignored = ignored;
    }
}
