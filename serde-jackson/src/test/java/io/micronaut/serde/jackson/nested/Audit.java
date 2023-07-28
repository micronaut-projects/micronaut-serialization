package io.micronaut.serde.jackson.nested;

import io.micronaut.serde.annotation.Serdeable;

import java.sql.Timestamp;
import java.util.Date;

@Serdeable
public class Audit {

    static final Timestamp MIN_TIMESTAMP = new Timestamp(new Date(0).getTime());

    private Long version  = 1L;

    // Init manually because cannot be nullable and not getting populated by the event
    private Timestamp dateCreated = MIN_TIMESTAMP;

    private Timestamp dateUpdated = MIN_TIMESTAMP;

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Timestamp getDateCreated() {
        return dateCreated;
    }

    public void setDateCreated(Timestamp dateCreated) {
        this.dateCreated = dateCreated;
    }

    public Timestamp getDateUpdated() {
        return dateUpdated;
    }

    public void setDateUpdated(Timestamp dateUpdated) {
        this.dateUpdated = dateUpdated;
    }
}
