package io.micronaut.serde.bson;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.List;
import java.util.UUID;

import io.micronaut.core.annotation.Introspected;

@Introspected
public class SampleData {

    private LocalDateTime localDateTime;

    private OffsetDateTime offsetDateTime;

    private LocalDate date;

    private LocalTime time;

    private UUID uuid;

    private String etag;

    private byte[] memo;

    private Period period;

    private Duration duration;

    private String description;

    private int grade;

    private Double rating;

    private List<Float> rates;

    private Address address;

    private Person person;

    private boolean active;

    public LocalDateTime getLocalDateTime() {
        return localDateTime;
    }

    public void setLocalDateTime(LocalDateTime localDateTime) {
        this.localDateTime = localDateTime;
    }

    public OffsetDateTime getOffsetDateTime() {
        return offsetDateTime;
    }

    public void setOffsetDateTime(OffsetDateTime offsetDateTime) {
        this.offsetDateTime = offsetDateTime;
    }

    public LocalDate getDate() {
        return date;
    }

    public LocalTime getTime() {
        return time;
    }

    public void setTime(LocalTime time) {
        this.time = time;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public String getEtag() {
        return etag;
    }

    public void setEtag(String etag) {
        this.etag = etag;
    }

    public byte[] getMemo() {
        return memo;
    }

    public void setMemo(byte[] memo) {
        this.memo = memo;
    }

    public Period getPeriod() {
        return period;
    }

    public void setPeriod(Period period) {
        this.period = period;
    }

    public Duration getDuration() {
        return duration;
    }

    public void setDuration(Duration duration) {
        this.duration = duration;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getGrade() {
        return grade;
    }

    public void setGrade(int grade) {
        this.grade = grade;
    }

    public Double getRating() {
        return rating;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

    public List<Float> getRates() {
        return rates;
    }

    public void setRates(List<Float> rates) {
        this.rates = rates;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public Person getPerson() {
        return person;
    }

    public void setPerson(Person person) {
        this.person = person;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
