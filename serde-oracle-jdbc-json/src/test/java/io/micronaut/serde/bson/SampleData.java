package io.micronaut.serde.bson;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.oracle.jdbc.json.serde.OracleJsonBinarySerde;
import io.micronaut.serde.oracle.jdbc.json.serde.OracleJsonBinaryStringSerde;
import io.micronaut.serde.oracle.jdbc.json.serde.OracleJsonDurationSerde;
import io.micronaut.serde.oracle.jdbc.json.serde.OracleJsonInstantSerde;
import io.micronaut.serde.oracle.jdbc.json.serde.OracleJsonLocaleDateSerde;
import io.micronaut.serde.oracle.jdbc.json.serde.OracleJsonLocaleDateTimeSerde;
import io.micronaut.serde.oracle.jdbc.json.serde.OracleJsonOffsetDateTimeSerde;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.util.UUID;

@Introspected
public class SampleData {

    @Serdeable.Deserializable(using = OracleJsonLocaleDateTimeSerde.class)
    @Serdeable.Serializable(using = OracleJsonLocaleDateTimeSerde.class)
    private LocalDateTime localDateTime;

    @Serdeable.Deserializable(using = OracleJsonOffsetDateTimeSerde.class)
    @Serdeable.Serializable(using = OracleJsonOffsetDateTimeSerde.class)
    private OffsetDateTime offsetDateTime;

    @Serdeable.Deserializable(using = OracleJsonLocaleDateSerde.class)
    @Serdeable.Serializable(using = OracleJsonLocaleDateSerde.class)
    private LocalDate date;

    @Serdeable.Deserializable(using = OracleJsonInstantSerde.class)
    @Serdeable.Serializable(using = OracleJsonInstantSerde.class)
    private Instant instant;

    private UUID uuid;

    @Serdeable.Deserializable(using = OracleJsonBinaryStringSerde.class)
    private String etag;

    @Serdeable.Deserializable(using = OracleJsonBinarySerde.class)
    @Serdeable.Serializable(using = OracleJsonBinarySerde.class)
    private byte[] memo;

    private Period period;

    @Serdeable.Deserializable(using = OracleJsonDurationSerde.class)
    @Serdeable.Serializable(using = OracleJsonDurationSerde.class)
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
