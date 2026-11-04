package io.micronaut.serde.oracle.jdbc.json;

import io.micronaut.serde.annotation.Serdeable;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZonedDateTime;

@Serdeable
public record OsonScalars(
    String str,
    String longStr,
    boolean yes,
    boolean no,
    @Nullable String nothing,
    int i,
    long l,
    long negative,
    BigInteger big,
    BigDecimal decimal,
    BigDecimal negativeDecimal,
    double dbl,
    double negativeDbl,
    float flt,
    byte[] bin,
    String binAsHex,
    LocalDateTime timestamp,
    LocalDateTime date,
    LocalDate day,
    LocalTime time,
    OffsetDateTime tz,
    ZonedDateTime zoned,
    Duration duration,
    Period period
) {
}
