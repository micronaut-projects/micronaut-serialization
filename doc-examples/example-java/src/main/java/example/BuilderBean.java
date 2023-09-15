package example;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.micronaut.core.annotation.Introspected;

import java.util.Objects;

@JsonDeserialize(builder = BuilderBean.Builder.class)
@Introspected(builder = @Introspected.IntrospectionBuilder(builderMethod = "builder"))
public class BuilderBean {
    private final String foo;

    private BuilderBean(String foo) {
        this.foo = foo;
    }

    public String getFoo() {
        return foo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BuilderBean bean = (BuilderBean) o;
        return Objects.equals(foo, bean.foo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(foo);
    }

    @Override
    public String toString() {
        return "BuilderBean{" +
            "foo='" + foo + '\'' +
            '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String foo;

        private Builder() {
        }

        public Builder foo(String foo) {
            this.foo = foo;
            return this;
        }

        public BuilderBean build() {
            return new BuilderBean(foo);
        }
    }
}
