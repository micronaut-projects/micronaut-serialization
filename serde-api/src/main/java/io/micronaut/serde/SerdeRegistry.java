package io.micronaut.serde;

/**
 * Represents a registry where specific serializers can be looked up.
 *
 * @author graemerocher
 * @since 1.0.0
 *
 */
public interface SerdeRegistry
        extends Serializer.EncoderContext,
                Deserializer.DecoderContext {
}
