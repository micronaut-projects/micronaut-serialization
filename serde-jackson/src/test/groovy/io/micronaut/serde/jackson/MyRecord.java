package io.micronaut.serde.jackson;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.time.Instant;
import java.util.Date;

@Serdeable
record MyRecord(String fooBar, int abcXyz, @Nullable Date theDate, @Nullable String otherStr) {

    // TODO: Adding this annotation fixes issue
    // @JsonCreator
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
