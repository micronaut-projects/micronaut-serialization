package io.micronaut.serde.protobuf;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.json.JsonMapper;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.protobuf.annotation.ProtoField;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProtobufMapperBehaviorTest {

    @Test
    void isExposedOnlyByItsConcreteBeanType() {
        try (ApplicationContext context = ApplicationContext.run()) {
            context.getBean(ProtobufMapper.class);
            assertFalse(context.containsBean(JsonMapper.class));
        }
    }

    @Test
    void rejectsANullTopLevelMessage() {
        try (ApplicationContext context = ApplicationContext.run()) {
            ProtobufMapper mapper = context.getBean(ProtobufMapper.class);
            assertThrows(Exception.class,
                () -> mapper.writeValueAsBytes(Argument.of(DefaultValues.class), null));
            assertThrows(Exception.class, () -> mapper.writeValueAsBytes((Object) null));
        }
    }

    @Test
    void omitsImplicitPresenceDefaults() throws Exception {
        try (ApplicationContext context = ApplicationContext.run()) {
            ProtobufMapper mapper = context.getBean(ProtobufMapper.class);
            assertArrayEquals(new byte[0], mapper.writeValueAsBytes(
                new DefaultValues("", 0, false, 0d)));
            assertArrayEquals(new byte[0], mapper.writeValueAsBytes(new Blob(new byte[0])));
        }
    }

    @Test
    void optionalPreservesExplicitDefaultPresence() throws Exception {
        try (ApplicationContext context = ApplicationContext.run()) {
            ProtobufMapper mapper = context.getBean(ProtobufMapper.class);
            assertArrayEquals(new byte[]{8, 0}, mapper.writeValueAsBytes(new OptionalValue(Optional.of(0))));
            assertArrayEquals(new byte[0], mapper.writeValueAsBytes(new OptionalValue(Optional.empty())));
            assertEquals(new OptionalValue(Optional.of(0)),
                mapper.readValue(new byte[]{8, 0}, Argument.of(OptionalValue.class)));
        }
    }

    @Test
    void primitiveOptionalsPreserveExplicitDefaultPresence() throws Exception {
        try (ApplicationContext context = ApplicationContext.run()) {
            ProtobufMapper mapper = context.getBean(ProtobufMapper.class);
            var present = new PrimitiveOptionals(OptionalInt.of(0), OptionalLong.of(0), OptionalDouble.of(0));
            byte[] expected = {8, 0, 16, 0, 25, 0, 0, 0, 0, 0, 0, 0, 0};

            assertArrayEquals(expected, mapper.writeValueAsBytes(present));
            assertEquals(present, mapper.readValue(expected, Argument.of(PrimitiveOptionals.class)));
            assertArrayEquals(new byte[0], mapper.writeValueAsBytes(new PrimitiveOptionals(
                OptionalInt.empty(), OptionalLong.empty(), OptionalDouble.empty())));
        }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void preservesGenericTypeBindingsInTheSchema() throws Exception {
        try (ApplicationContext context = ApplicationContext.run()) {
            ProtobufMapper mapper = context.getBean(ProtobufMapper.class);
            Argument<GenericBox<List<Integer>>> type = (Argument) Argument.of(
                GenericBox.class,
                Argument.listOf(Integer.class)
            );
            var value = new GenericBox<>(List.of(1, 2, 3));

            assertEquals(value, mapper.readValue(mapper.writeValueAsBytes(type, value), type));
        }
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void preservesMultipleGenericTypeBindingsInTheSchema() throws Exception {
        try (ApplicationContext context = ApplicationContext.run()) {
            ProtobufMapper mapper = context.getBean(ProtobufMapper.class);
            Argument<GenericPair<String, List<Integer>>> type = (Argument) Argument.of(
                GenericPair.class,
                Argument.of(String.class),
                Argument.listOf(Integer.class)
            );
            var value = new GenericPair<>("value", List.of(1, 2, 3));

            assertEquals(value, mapper.readValue(mapper.writeValueAsBytes(type, value), type));
        }
    }

    @Serdeable
    record DefaultValues(
        @ProtoField(1) String name,
        @ProtoField(2) int count,
        @ProtoField(3) boolean active,
        @ProtoField(4) double ratio
    ) {
    }

    @Serdeable
    record OptionalValue(@ProtoField(1) Optional<Integer> value) {
    }

    @Serdeable
    record PrimitiveOptionals(
        @ProtoField(1) OptionalInt intValue,
        @ProtoField(2) OptionalLong longValue,
        @ProtoField(3) OptionalDouble doubleValue
    ) {
    }

    @Serdeable
    record GenericBox<T>(@ProtoField(1) T value) {
    }

    @Serdeable
    record GenericPair<F, S>(@ProtoField(1) F first, @ProtoField(2) S second) {
    }
}
