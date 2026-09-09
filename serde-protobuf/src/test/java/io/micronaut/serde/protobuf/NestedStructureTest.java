package io.micronaut.serde.protobuf;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Nested messages are length-prefixed, so each one is buffered and copied into its parent when it
 * finishes, and those buffers are pooled and reused. Deep and wide nesting is what would expose a
 * buffer being reused while something still refers to it.
 */
class NestedStructureTest {

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

    private static Org org() {
        List<Team> teams = IntStream.range(0, 12)
            .mapToObj(t -> new Team("team-" + t, IntStream.range(0, 5)
                .mapToObj(a -> new Address("street-" + t + "-" + a, "city-" + a))
                .toList()))
            .toList();
        return new Org("Analytical Engine Co", teams);
    }

    private static byte[] reference(Org org) {
        Descriptors.Descriptor orgType = ProtoReference.ORG;
        Descriptors.Descriptor teamType = ProtoReference.TEAM;
        Descriptors.Descriptor addressType = ProtoReference.ADDRESS;

        List<DynamicMessage> teams = org.teams().stream()
            .map(team -> DynamicMessage.newBuilder(teamType)
                .setField(teamType.findFieldByNumber(1), team.name())
                .setField(teamType.findFieldByNumber(2), team.members().stream()
                    .map(address -> DynamicMessage.newBuilder(addressType)
                        .setField(addressType.findFieldByNumber(1), address.street())
                        .setField(addressType.findFieldByNumber(2), address.city())
                        .build())
                    .toList())
                .build())
            .toList();

        return DynamicMessage.newBuilder(orgType)
            .setField(orgType.findFieldByNumber(1), org.name())
            .setField(orgType.findFieldByNumber(2), teams)
            .build()
            .toByteArray();
    }

    @Test
    void encodesThreeLevelsOfNesting() throws Exception {
        Org org = org();
        assertArrayEquals(reference(org), mapper.writeValueAsBytes(Argument.of(Org.class), org));
    }

    @Test
    void decodesThreeLevelsOfNesting() throws Exception {
        Org org = org();
        assertEquals(org, mapper.readValue(reference(org), Argument.of(Org.class)));
    }

    @Test
    void repeatedEncodingIsStableAcrossCalls() throws Exception {
        // buffers are pooled per call; a stale buffer would show up as drift between runs
        Org org = org();
        byte[] expected = reference(org);
        for (int i = 0; i < 50; i++) {
            assertArrayEquals(expected, mapper.writeValueAsBytes(Argument.of(Org.class), org), "run " + i);
        }
    }
}
