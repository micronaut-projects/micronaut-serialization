/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */

package io.micronaut.serde.json.stream;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.json.bind.annotation.JsonbCreator;
import jakarta.json.bind.annotation.JsonbDateFormat;
import jakarta.json.bind.annotation.JsonbNillable;
import jakarta.json.bind.annotation.JsonbNumberFormat;
import jakarta.json.bind.annotation.JsonbProperty;
import jakarta.json.bind.annotation.JsonbPropertyOrder;
import jakarta.json.bind.annotation.JsonbTransient;
import java.time.Instant;
import java.time.LocalDate;

/**
 *
 * @author gkrocher
 */
@JsonbPropertyOrder({"name", "price", "dateCreated"}) 
@Serdeable
@JsonbNillable
public class JsonbTest {

    @JsonbProperty("n")
    private final String name;
    @JsonbDateFormat("ddMMyy")
    private LocalDate dateCreated = LocalDate.of(2021, 10, 1);
    @JsonbNumberFormat("$##.##")
    private double price = 1.1;
    @JsonbTransient
    private boolean ignored;
    // should be included due to JsonbNillable
    private final String other = null;

    @JsonbCreator
    public JsonbTest(String name) {
        this.name = name;
    }

    public String getOther() {
        return other;
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

    public LocalDate getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(LocalDate dateCreated) {
        this.dateCreated = dateCreated;
    }
}
