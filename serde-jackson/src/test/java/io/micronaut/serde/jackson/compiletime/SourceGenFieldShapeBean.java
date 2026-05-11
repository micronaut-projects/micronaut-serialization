package io.micronaut.serde.jackson.compiletime;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.SerdeableGenerated;

import java.util.List;

@SerdeableGenerated
@Introspected(accessKind = Introspected.AccessKind.FIELD)
public class SourceGenFieldShapeBean {
    String value;
    int count;
    List<String> tags;
}

@SerdeableGenerated
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class SourceGenGeneratedFieldDefaults {
    String name = "default-name";
    boolean active = true;
    int count = 7;
    @Nullable
    String nullableName = "default-nullable-name";
    @Nullable
    Boolean nullableActive = Boolean.TRUE;
}

@SerdeableGenerated
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class SourceGenRuntimeFieldDefaults {
    String name = "default-name";
    boolean active = true;
    int count = 7;
    @Nullable
    String nullableName = "default-nullable-name";
    @Nullable
    Boolean nullableActive = Boolean.TRUE;
}
