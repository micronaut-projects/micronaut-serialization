package io.micronaut.serde.protobuf;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.protobuf.annotation.ProtoField;

import java.math.BigDecimal;
import java.math.BigInteger;

@Serdeable
public record BigNumberHolder(@ProtoField(1) @Nullable BigInteger big,
                              @ProtoField(2) @Nullable BigDecimal precise) {
}
