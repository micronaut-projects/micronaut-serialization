package io.micronaut.serde.jmespath.tck

import io.micronaut.core.type.Argument
import io.micronaut.serde.LimitingStream
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.jmespath.SerdeJmesPathDecoder
import io.micronaut.serde.jmespath.SerdeJmesPathSyntaxException
import io.micronaut.serde.support.util.JsonNodeDecoder
import io.micronaut.serde.support.util.JsonNodeToStringUtil
import spock.lang.Shared
import spock.lang.Specification

class TckSpec extends Specification {

    @Shared
    ObjectMapper objectMapper = ObjectMapper.create(Map.of(), "io.micronaut.serde.jmespath.tck")

    private static def TCK_FILES_LIST = List.of(
//            "basic.json",
//            "benchmarks.json",
//            "boolean.json",
//            "current.json",
//            "escape.json",
//            "filters.json",
//            "functions.json",
//            "identifiers.json",
//            "indices.json",
//            "literal.json",
            "multiselect.json",
//            "pipe.json",
//            "slice.json",
//            "syntax.json",
//            "unicode.json",
//            "wildcard.json"
    )

    def '#tckTest.name, given: #tckTest.givenAsString expression: "#tckTest.expression" result: #tckTest.resultAsString '(TckTest tckTest) {
        when:
            def givenDecoder = JsonNodeDecoder.create(tckTest.given(), LimitingStream.DEFAULT_LIMITS)
        then:
            if (tckTest.error() == null) {
                def result = SerdeJmesPathDecoder.decode(givenDecoder, tckTest.expression())
                def expected = JsonNodeToStringUtil.toString(tckTest.result())
                def actual = JsonNodeToStringUtil.toString(result)
                assert actual == expected
            } else {
                try {
                    SerdeJmesPathDecoder.decode(givenDecoder, tckTest.expression())
                } catch (Exception e) {
                    if ("syntax" == tckTest.error()) {
                        assert e instanceof SerdeJmesPathSyntaxException
                    } else {
                        assert tckTest.error() == e.message
                    }
                }
            }

        where:
            tckTest << fetchAllTckTests()
    }

    private List<TckTest> fetchAllTckTests() {
        def list = TCK_FILES_LIST.stream().flatMap { String fileName ->
            return objectMapper.readValue(
                    getClass().getResourceAsStream("/tck/compliance/" + fileName),
                    Argument.listOf(TckDefinition)
            ).stream().flatMap { it.toTests(fileName.replace(".json", "")).stream() }

        }.toList()
        return list
    }

}
