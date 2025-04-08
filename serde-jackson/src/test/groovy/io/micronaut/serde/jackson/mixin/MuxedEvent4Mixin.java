package io.micronaut.serde.jackson.mixin;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.SerdeImport;
import io.micronaut.serde.jackson.mixin.outer.MuxedEvent4;

@Introspected(accessKind = Introspected.AccessKind.FIELD, visibility = Introspected.Visibility.ANY, targetPackage = "io.micronaut.serde.jackson.mixin.outer")
public class MuxedEvent4Mixin {
}

@SerdeImport(mixin = MuxedEvent4Mixin.class, value = MuxedEvent4.class)
class TestMixin4 {
}
