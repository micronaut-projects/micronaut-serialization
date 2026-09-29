package io.micronaut.serde.jackson.nonpublicsetter;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Jackson-only model with non-public {@link JsonSetter} hooks, compiled ahead of the in-memory
 * compilation of the specs, which sees it as a binary class.
 */
public final class LegacyJacksonValue {
    @JsonIgnore
    private String type;

    private Object value;

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXTERNAL_PROPERTY,
        property = "type"
    )
    @JsonSubTypes({@JsonSubTypes.Type(value = String.class, name = "STRING")})
    public Object getValue() {
        return value;
    }

    @JsonSetter("value")
    void setValue(Object value) {
        this.value = value;
    }

    @JsonGetter("type")
    public String getSerializationTypeName() {
        return type;
    }

    @JsonSetter("type")
    private void setDeserializedTypeByName(String typeName) {
        this.type = typeName;
    }
}
