package io.micronaut.serde.jackson.outerinterface;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME, property = "type"
)
public sealed interface RecordCommandBroken {
  String fooBar();

  @JsonTypeName("print")
  record PrintCommand(String foo) implements RecordCommandBroken {

    @Override
    @JsonProperty
    public String fooBar() {
      return "test";
    }
  }
}
