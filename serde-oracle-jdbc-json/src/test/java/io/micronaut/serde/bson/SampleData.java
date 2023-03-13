package io.micronaut.serde.bson;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.UUID;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.oracle.jdbc.json.annotation.OracleType;

import static io.micronaut.serde.oracle.jdbc.json.annotation.OracleType.Type.*;

@Introspected
public class SampleData {

    @OracleType(TEMPORAL)
    private LocalDateTime localDateTime;

    @OracleType(TEMPORAL)
    private OffsetDateTime offsetDateTime;

    @OracleType(TEMPORAL)
    private LocalDate date;

    @OracleType(TEMPORAL)
    private Instant instant;

    private UUID uuid;

    @OracleType(BASE16_STRING)
    private String etag;

    @OracleType(BINARY)
    private byte[] memo;

    private Period period;

    private Duration duration;

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

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public Instant getInstant() {
        return instant;
    }

    public void setInstant(Instant instant) {
        this.instant = instant;
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
}
