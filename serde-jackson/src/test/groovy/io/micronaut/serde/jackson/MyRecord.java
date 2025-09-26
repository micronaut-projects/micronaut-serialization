package io.micronaut.serde.jackson;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.Date;

@Serdeable
record MyRecord(String fooBar, int abcXyz, @Nullable Date theDate, @Nullable String otherStr) {

    MyRecord {
        if (theDate == null) {
            theDate = Date.from(Instant.now());
        }
    }

    public MyRecord(String fooBar, int abcXyz, Date theDate) {
        this(fooBar, abcXyz, theDate, "random");
    }

    public MyRecord(String fooBar, int abcXyz) {
        this(fooBar, abcXyz, Date.from(Instant.now()), "random");
    }
}
