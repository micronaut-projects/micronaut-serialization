package io.micronaut.serde.jackson.specific;

import io.micronaut.serde.annotation.SerdeableGenerated;

/**
 * A generated serde whose property is deserialized with identities of the document.
 */
@SerdeableGenerated(required = false)
public record IdentityGroupHolder(String label, IdentityGroup group) {
}
