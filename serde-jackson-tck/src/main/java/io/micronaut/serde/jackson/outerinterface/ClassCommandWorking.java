package io.micronaut.serde.jackson.outerinterface;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME, property = "type"
)
public interface ClassCommandWorking {
  String getFooBar();

  @JsonTypeName("print")
  final class PrintCommand implements ClassCommandWorking {
    private String foo;

    public PrintCommand(String foo) {
      this.foo = foo;
    }

    public String getFoo() {
      return foo;
    }

    public void setFoo(String foo) {
      this.foo = foo;
    }

    @Override
    public String getFooBar() {
      return "foobar";
    }
  }
}
