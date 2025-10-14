package io.micronaut.serde.jackson;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonInclude(JsonInclude.Include.ALWAYS)
public class MyBeanConstructorWithUnwrappedAlwaysInclude {

    private String name;
    @JsonUnwrapped
    private InnerAlwaysInclude inner1;
    @JsonUnwrapped
    private InnerDefaultInclude inner2;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public InnerAlwaysInclude getInner1() {
        return inner1;
    }

    public void setInner1(InnerAlwaysInclude inner1) {
        this.inner1 = inner1;
    }

    public InnerDefaultInclude getInner2() {
        return inner2;
    }

    public void setInner2(InnerDefaultInclude inner2) {
        this.inner2 = inner2;
    }

    @Serdeable
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public static final class InnerAlwaysInclude {
        private String key1;
        private String value1;

        public String getKey1() {
            return key1;
        }

        public void setKey1(String key1) {
            this.key1 = key1;
        }

        public String getValue1() {
            return value1;
        }

        public void setValue1(String value1) {
        }

    }

    @Serdeable
    public static final class InnerDefaultInclude {
        private String key2;
        private String value2;

        public String getKey2() {
            return key2;
        }

        public void setKey2(String key2) {
            this.key2 = key2;
        }

        public String getValue2() {
            return value2;
        }

        public void setValue2(String value2) {
        }

    }
}
