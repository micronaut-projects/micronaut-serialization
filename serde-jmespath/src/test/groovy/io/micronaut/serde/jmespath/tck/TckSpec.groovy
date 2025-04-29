package io.micronaut.serde.jmespath.tck

import io.micronaut.core.type.Argument
import io.micronaut.serde.LimitingStream
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.jmespath.SerdeJmesPathDecoder
import io.micronaut.serde.support.util.JsonNodeDecoder
import io.micronaut.serde.support.util.JsonNodeToStringUtil
import spock.lang.Shared
import spock.lang.Specification

class TckSpec extends Specification {

    @Shared
    ObjectMapper objectMapper = ObjectMapper.create(Map.of(), "io.micronaut.serde.jmespath.tck")

    def 'given: #tckTest.givenAsString expression: "#tckTest.expression" result: #tckTest.resultAsString'(TckTest tckTest) {
        when:
            def givenDecoder = JsonNodeDecoder.create(tckTest.given(), LimitingStream.DEFAULT_LIMITS)
        then:
            def result = SerdeJmesPathDecoder.decode(givenDecoder, tckTest.expression())
            def expected = JsonNodeToStringUtil.toString(tckTest.result())
            def actual = JsonNodeToStringUtil.toString(result)
            actual == expected
        where:
            tckTest << objectMapper.readValue(
                    getClass().getResourceAsStream("/tck/compliance/basic.json"),
                    Argument.listOf(TckDefinition)
            ).stream().flatMap { it.toTests().stream() }.toList()
    }

}
