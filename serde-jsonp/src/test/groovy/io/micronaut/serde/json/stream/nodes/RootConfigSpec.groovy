package io.micronaut.serde.json.stream.nodes

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class RootConfigSpec extends Specification {

    @Inject
    RootConfig rootConfig

    void "test config injection"() {
        expect:
        rootConfig.someValue == "foo"
        rootConfig.someNested
        rootConfig.someNested.nestedValue == "bar"
        rootConfig.someListNested
        rootConfig.someListNested.size() == 2
        rootConfig.someListNested[0].nestedListValue == "baz1"
        rootConfig.someListNested[1].nestedListValue == "baz2"
        rootConfig.someRawNested
        rootConfig.someRawNested.size() == 2
        rootConfig.someRawNested.get('abc_def').rawNestedValue == "abc123"
        rootConfig.someRawNested.get('def_ghi').rawNestedValue == "def456"
    }
}
