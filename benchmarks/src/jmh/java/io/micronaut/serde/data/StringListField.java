package io.micronaut.serde.data;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.SerdeableGenerated;

import java.util.List;

@Introspected(accessKind = Introspected.AccessKind.FIELD)
@SerdeableGenerated
public class StringListField {

    public List<String> strs;
}
