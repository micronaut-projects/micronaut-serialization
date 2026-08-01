package io.micronaut.serde.bson;

import io.micronaut.serde.annotation.Serdeable;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

/**
 * Polymorphic base matching the MongoDB POJO convention of placing
 * {@link BsonDiscriminator} on the type and all of its subtypes.
 */
@Serdeable
@BsonDiscriminator("room_state")
public abstract class RoomState {

    private String label;

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
