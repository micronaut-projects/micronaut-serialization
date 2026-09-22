package io.micronaut.serde.jackson.nullablenested;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

/**
 * A setter bean with a non-nullable unwrapped constructor bean.
 */
@Serdeable
public class UnwrappedSetterOuter {

    @Nullable
    private String id;

    @JsonUnwrapped
    private UnwrappedSetterInner inner;

    public @Nullable String getId() {
        return id;
    }

    public void setId(@Nullable String id) {
        this.id = id;
    }

    public UnwrappedSetterInner getInner() {
        return inner;
    }

    public void setInner(UnwrappedSetterInner inner) {
        this.inner = inner;
    }
}
