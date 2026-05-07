package io.micronaut.serde.jackson.compiletime;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.SerdeableGenerated;

import java.util.List;

@SerdeableGenerated
@Introspected(accessKind = Introspected.AccessKind.FIELD)
public class SourceGenFieldShapeBean {
    String value;
    int count;
    List<String> tags;
}
