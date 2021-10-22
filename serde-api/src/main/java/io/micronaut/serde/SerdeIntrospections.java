package io.micronaut.serde;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.type.Argument;

public interface SerdeIntrospections {
    /**
     * Gets an introspection for the given type for serialization.
     * @param type The type
     * @param <T> The generic type
     * @return The introspection, never {@code null}
     * @throws io.micronaut.core.beans.exceptions.IntrospectionException if no introspection exists
     */
    @NonNull
    <T> BeanIntrospection<T> getSerializableIntrospection(@NonNull Argument<T> type);

    /**
     * Gets an introspection for the given type for serialization.
     * @param type The type
     * @param <T> The generic type
     * @return The introspection, never {@code null}
     * @throws io.micronaut.core.beans.exceptions.IntrospectionException if no introspection exists
     */
    @NonNull <T> BeanIntrospection<T> getDeserializableIntrospection(@NonNull Argument<T> type);
}
