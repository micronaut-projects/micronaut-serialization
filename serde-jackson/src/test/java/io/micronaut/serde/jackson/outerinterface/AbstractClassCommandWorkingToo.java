package io.micronaut.serde.jackson.outerinterface;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME, property = "type"
)
public abstract class AbstractClassCommandWorkingToo {
  public abstract String getFooBar();

  @JsonTypeName("print")
  final static class PrintCommand extends AbstractClassCommandWorkingToo {
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
