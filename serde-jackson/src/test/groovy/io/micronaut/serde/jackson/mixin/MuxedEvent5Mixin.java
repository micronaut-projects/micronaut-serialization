package io.micronaut.serde.jackson.mixin;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.SerdeImport;
import io.micronaut.serde.jackson.mixin.outer.MuxedEvent5;

@Introspected(accessKind = Introspected.AccessKind.FIELD, visibility = Introspected.Visibility.ANY, targetPackage = "io.micronaut.serde.jackson.mixin.outer")
public class MuxedEvent5Mixin {

    @io.micronaut.core.annotation.ReflectiveAccess
    String compartment;
    @io.micronaut.core.annotation.ReflectiveAccess
    String content;

}

@SerdeImport(mixin = MuxedEvent5Mixin.class, value = MuxedEvent5.class)
class TestMixin5 {
}
