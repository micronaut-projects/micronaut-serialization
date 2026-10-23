package io.micronaut.serde.jackson.specific;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

/**
 * A bean that refers to an identity of the document by its identifier.
 */
@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
public class IdentityGroup {
    public IdentityNode owner;
    public IdentityNode manager;
}
