package io.micronaut.serde.jackson.specific;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

/**
 * A bean with an identity, resolved within one document.
 */
@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
public class IdentityNode {
    public int id;
    public String name;
}
