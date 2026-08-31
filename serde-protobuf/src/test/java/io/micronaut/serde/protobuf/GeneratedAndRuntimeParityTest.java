package io.micronaut.serde.protobuf;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The compile-time generated serdes and the runtime serdes reach the encoder through different
 * call paths, so both have to produce the same bytes for the same shape.
 */
class GeneratedAndRuntimeParityTest {

    private static ApplicationContext context;
    private static ProtobufMapper mapper;

    @BeforeAll
    static void setup() {
        context = ApplicationContext.run();
        mapper = context.getBean(ProtobufMapper.class);
    }

    @AfterAll
    static void cleanup() {
        context.close();
    }

    @Test
    void bothPathsProduceTheSameBytes() throws Exception {
        byte[] generated = mapper.writeValueAsBytes(Argument.of(GeneratedPoint.class), new GeneratedPoint(300, -7));
        byte[] runtime = mapper.writeValueAsBytes(Argument.of(ImportedPoint.class), new ImportedPoint(300, -7));
        assertArrayEquals(generated, runtime);
    }

    @Test
    void bothPathsReadEachOthersBytes() throws Exception {
        byte[] generated = mapper.writeValueAsBytes(Argument.of(GeneratedPoint.class), new GeneratedPoint(300, -7));
        assertEquals(new ImportedPoint(300, -7), mapper.readValue(generated, Argument.of(ImportedPoint.class)));

        byte[] runtime = mapper.writeValueAsBytes(Argument.of(ImportedPoint.class), new ImportedPoint(300, -7));
        assertEquals(new GeneratedPoint(300, -7), mapper.readValue(runtime, Argument.of(GeneratedPoint.class)));
    }
}
