package io.micronaut.serde.jmespath.tck;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.jmespath.SerdeJmesPathDecoder;
import io.micronaut.serde.jmespath.SerdeJmesPathSyntaxException;
import io.micronaut.serde.support.util.JsonNodeToStringUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public abstract class JmespathTckTest {

    private static final List<String> TCK_FILES_LIST = List.of(
//        "basic.json", // passing
//            "benchmarks.json",
//            "boolean.json",
//            "current.json",
//            "escape.json",
//            "filters.json",
//            "functions.json",
//        "identifiers.json", // passing
//        "indices.json", // passing
//            "literal.json",
        "multiselect.json" // passing except one test because we wrap array by default
//            "pipe.json",
//        "slice.json",
//            "syntax.json",
//        "unicode.json", // passing
//        "wildcard.json" // passing
    );

    protected abstract Decoder createDecoder(String json) throws IOException;

    @ParameterizedTest
    @MethodSource("tckData")
    void jmesPathTck(TckTest tckTest) throws IOException {
        String givenAsString = tckTest.getGivenAsString();
        var givenDecoder = createDecoder(givenAsString);
        if (tckTest.error() == null) {
            var result = SerdeJmesPathDecoder.decode(givenDecoder, tckTest.expression());
            var expected = JsonNodeToStringUtil.toString(tckTest.result());
            var actual = JsonNodeToStringUtil.toString(result);
            Assertions.assertEquals(expected, actual);
        } else {
            try {
                SerdeJmesPathDecoder.decode(givenDecoder, tckTest.expression());
            } catch (Exception e) {
                if ("syntax".equals(tckTest.error())) {
                    Assertions.assertInstanceOf(SerdeJmesPathSyntaxException.class, e);
                } else {
                    Assertions.assertEquals(tckTest.error(), e.getMessage());
                }
            }
        }
    }

    private static Stream<Arguments> tckData() {
        ObjectMapper objectMapper = ObjectMapper.create(Map.of(), "io.micronaut.serde.jmespath.tck");
        return TCK_FILES_LIST.stream().flatMap(fileName ->
            {
                try {
                    return objectMapper.readValue(
                        JmespathTckTest.class.getResourceAsStream("/tck/compliance/" + fileName),
                        Argument.listOf(TckDefinition.class)
                    ).stream().flatMap(it -> it.toTests(fileName.replace(".json", "")).stream());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }).map(Arguments::of)
            .skip(3).findFirst().stream()
            ;
    }

}
