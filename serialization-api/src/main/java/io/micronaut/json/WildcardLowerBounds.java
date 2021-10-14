package io.micronaut.json;

import java.util.Arrays;
import java.util.Objects;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.DefaultArgument;

@Internal
final class WildcardLowerBounds<T> extends DefaultArgument<T> implements WildcardArgument<T> {
    private final Argument<?>[] lowerBounds;

    WildcardLowerBounds(Class<T> type,
                        String name,
                        Argument<?>... lowerBounds) {
        super(type, name, AnnotationMetadata.EMPTY_METADATA, Argument.ZERO_ARGUMENTS);
        this.lowerBounds = lowerBounds;
    }

    @Override
    public Argument<?>[] getLowerBounds() {
        return lowerBounds;
    }

    @Override
    public boolean equalsType(Argument<?> o) {
        if (o instanceof WildcardLowerBounds) {
            return super.equalsType(o) && Arrays.equals(lowerBounds, ((WildcardLowerBounds<?>) o).lowerBounds);
        } else {
            return false;
        }
    }

    @Override
    public int typeHashCode() {
        return Objects.hash(getType(), Arrays.hashCode(getTypeParameters()), Arrays.hashCode(lowerBounds));
    }
}
