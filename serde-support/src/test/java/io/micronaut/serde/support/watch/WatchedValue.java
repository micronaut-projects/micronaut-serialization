package io.micronaut.serde.support.watch;

import io.micronaut.serde.annotation.Serdeable;

/**
 * A type the runtime object serdes serialize until a serializer for it is registered.
 *
 * @param name The name
 */
@Serdeable
public record WatchedValue(String name) {
}
