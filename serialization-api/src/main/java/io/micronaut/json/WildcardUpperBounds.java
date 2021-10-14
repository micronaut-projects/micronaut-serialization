package io.micronaut.json;

import java.util.Arrays;
import java.util.Objects;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.DefaultArgument;

@Internal
final class WildcardUpperBounds<T> extends DefaultArgument<T> implements WildcardArgument<T> {
    private final Argument<?>[] upperBounds;

    WildcardUpperBounds(Class<T> type,
                        String name,
                        Argument<?>... upperBounds) {
        super(type, name, AnnotationMetadata.EMPTY_METADATA, Argument.ZERO_ARGUMENTS);
        this.upperBounds = upperBounds;
    }

    @Override
    public Argument<?>[] getUpperBounds() {
        return upperBounds;
    }

    @Override
    public boolean equalsType(Argument<?> o) {
        if (o instanceof WildcardUpperBounds) {
            return super.equalsType(o) && Arrays.equals(upperBounds, ((WildcardUpperBounds<?>) o).upperBounds);
        } else {
            return false;
        }
    }

    @Override
    public int typeHashCode() {
        return Objects.hash(getType(), Arrays.hashCode(getTypeParameters()), Arrays.hashCode(upperBounds));
    }
}
