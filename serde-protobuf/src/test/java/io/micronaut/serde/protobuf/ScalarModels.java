package io.micronaut.serde.protobuf;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.Year;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.TimeZone;
import java.util.UUID;

/**
 * One record per family of Java scalar, all numbered by declaration order.
 */
final class ScalarModels {

    private ScalarModels() {
    }

    enum Colour { RED, GREEN, BLUE }

    /**
     * Every Java primitive, in the order of the {@code Scalars} reference message.
     */
    @Serdeable
    record Primitives(boolean flag,
                      byte tiny,
                      short small,
                      char letter,
                      int whole,
                      long big,
                      float single,
                      double wide,
                      String text,
                      byte[] blob) {
    }

    @Serdeable
    record Boxed(Boolean flag,
                 Byte tiny,
                 Short small,
                 Character letter,
                 Integer whole,
                 Long big,
                 Float single,
                 Double wide) {
    }

    @Serdeable
    record BigNumbers(BigInteger big, BigDecimal precise) {
    }

    @Serdeable
    record Enums(Colour colour) {
    }

    @Serdeable
    record Temporal(Instant instant,
                    LocalDate date,
                    LocalDateTime dateTime,
                    LocalTime time,
                    ZonedDateTime zoned,
                    OffsetDateTime offset,
                    Year year,
                    Duration duration,
                    Date legacy,
                    TimeZone zone) {
    }

    @Serdeable
    record Misc(UUID id, URI uri, Locale locale, Charset charset) {
    }

    @Serdeable
    record Optionals(Optional<String> text, OptionalInt count, OptionalLong big, OptionalDouble ratio) {
    }

    @Serdeable
    record Nullables(String text, Integer whole, Address address) {
    }

    /**
     * The same scalars declared so that presence is explicit, which is what keeps a value equal to
     * its type's default on the wire.
     */
    @Serdeable
    record ExplicitPresence(@Nullable String text,
                            @Nullable Integer whole,
                            @Nullable Boolean flag,
                            @Nullable byte[] blob) {
    }
}
