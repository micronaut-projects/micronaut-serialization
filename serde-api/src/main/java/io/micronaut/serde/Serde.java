package io.micronaut.serde;

/**
 * Combined interface for a serializer and deserializer pair.
 * @param <T> The type
 * @since 1.0.0
 */
public interface Serde<T> extends Serializer<T>, Deserializer<T> {
}
