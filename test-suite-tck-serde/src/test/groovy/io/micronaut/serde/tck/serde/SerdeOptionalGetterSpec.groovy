package io.micronaut.serde.tck.serde

import io.micronaut.serde.jackson.OptionalGetterSpec
import spock.lang.Ignore

@Ignore("Reproducer for issue with Optional getter wrapping String property - fails in pure Serde, passes in Jackson Databind")
class SerdeOptionalGetterSpec extends OptionalGetterSpec {
}
