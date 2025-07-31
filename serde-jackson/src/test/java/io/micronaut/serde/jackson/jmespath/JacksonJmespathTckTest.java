package io.micronaut.serde.jackson.jmespath;

import com.fasterxml.jackson.core.JsonFactoryBuilder;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.jackson.JacksonDecoder;
import io.micronaut.serde.jmespath.tck.JmespathTckTest;

import java.io.IOException;

public class JacksonJmespathTckTest extends JmespathTckTest {
    @Override
    protected Decoder createDecoder(String json) throws IOException {
        return JacksonDecoder.create(new JsonFactoryBuilder().build().createParser(json), LimitingStream.DEFAULT_LIMITS);
    }
}
