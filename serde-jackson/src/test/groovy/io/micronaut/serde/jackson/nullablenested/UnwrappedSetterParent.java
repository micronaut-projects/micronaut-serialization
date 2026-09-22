package io.micronaut.serde.jackson.nullablenested;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public class UnwrappedSetterParent {

    @Nullable
    private UnwrappedSetterOuter outer;

    public @Nullable UnwrappedSetterOuter getOuter() {
        return outer;
    }

    public void setOuter(@Nullable UnwrappedSetterOuter outer) {
        this.outer = outer;
    }
}
