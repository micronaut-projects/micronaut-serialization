package io.micronaut.serde.jackson;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
record MyBean(String fooBar, int abcXyz) {

}
