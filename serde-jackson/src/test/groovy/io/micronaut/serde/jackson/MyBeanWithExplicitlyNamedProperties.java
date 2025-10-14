package io.micronaut.serde.jackson;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
record MyBeanWithExplicitlyNamedProperties(@JsonProperty("explicit_foo_bar_prop_name") String fooBar, int abcXyz) {

}
