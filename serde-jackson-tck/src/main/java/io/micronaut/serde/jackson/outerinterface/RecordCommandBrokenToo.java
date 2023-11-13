package io.micronaut.serde.jackson.outerinterface;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME, property = "type"
)
public sealed interface RecordCommandBrokenToo {

  @JsonTypeName("print")
  record PrintCommand(String foo) implements RecordCommandBrokenToo {
  }
}
