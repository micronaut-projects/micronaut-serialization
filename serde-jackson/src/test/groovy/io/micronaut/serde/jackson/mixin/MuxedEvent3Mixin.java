package io.micronaut.serde.jackson.mixin;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.SerdeImport;

@Introspected(accessKind = Introspected.AccessKind.FIELD, visibility = Introspected.Visibility.ANY)
class MuxedEvent3Mixin {
}

@SerdeImport(mixin = MuxedEvent3Mixin.class, value = MuxedEvent3.class)
class TestMixin3 {
}
