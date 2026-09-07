package io.micronaut.serde.protobuf;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code value} and {@code position} are aliases for one another, and a property that names neither
 * is numbered by where it sits among the message's properties.
 */
class FieldPositionTest {

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
    void valueAndPositionAreTheSameMember() throws Exception {
        ProtoSchema byValue = ProtoSchema.of(FieldPositionModels.ByValue.class);
        ProtoSchema byPosition = ProtoSchema.of(FieldPositionModels.ByPosition.class);

        assertEquals(3, byValue.byName("name").number());
        assertEquals(7, byValue.byName("count").number());
        assertEquals(3, byPosition.byName("name").number());
        assertEquals(7, byPosition.byName("count").number());
    }

    @Test
    void bothSpellingsProduceTheSameBytes() throws Exception {
        byte[] fromValue = mapper.writeValueAsBytes(
            Argument.of(FieldPositionModels.ByValue.class), new FieldPositionModels.ByValue("abc", 42));
        byte[] fromPosition = mapper.writeValueAsBytes(
            Argument.of(FieldPositionModels.ByPosition.class), new FieldPositionModels.ByPosition("abc", 42));

        assertArrayEquals(fromValue, fromPosition);
    }

    @Test
    void unnumberedPropertiesCountFromOne() throws Exception {
        ProtoSchema schema = ProtoSchema.of(FieldPositionModels.ByOrder.class);

        assertEquals(1, schema.byName("name").number());
        assertEquals(2, schema.byName("count").number());
    }

    @Test
    void aPropertyCanChooseItsTypeWithoutChoosingItsNumber() throws Exception {
        ProtoSchema schema = ProtoSchema.of(FieldPositionModels.TypedButUnpositioned.class);

        assertEquals(1, schema.byName("temperature").number());
        assertEquals(2, schema.byName("note").number());
        // the type is still honoured: one tag byte plus two zig-zag bytes, not ten sign-extended ones
        assertEquals(3, mapper.writeValueAsBytes(
            Argument.of(FieldPositionModels.TypedButUnpositioned.class),
            new FieldPositionModels.TypedButUnpositioned(-4250, null)).length);
    }

    @Test
    void explicitAndDerivedNumbersCoexist() throws Exception {
        ProtoSchema schema = ProtoSchema.of(FieldPositionModels.Mixed.class);

        assertEquals(10, schema.byName("first").number());
        assertEquals(2, schema.byName("second").number());
        assertEquals(11, schema.byName("third").number());
        assertEquals(4, schema.byName("fourth").number());
    }

    @Test
    void mutableBeansAreNumberedByIntrospectionOrder() throws Exception {
        ProtoSchema schema = ProtoSchema.of(FieldPositionModels.MutableBean.class);

        assertEquals(1, schema.byName("alpha").number());
        assertEquals(2, schema.byName("beta").number());
        assertEquals(3, schema.byName("gamma").number());
    }

    @Test
    void derivedNumberingMatchesTheEquivalentSchema() throws Exception {
        // what protoc would produce for: message { string name = 1; int32 count = 2; }
        Descriptors.Descriptor descriptor = ProtoReference.NAME_AND_COUNT;
        byte[] expected = DynamicMessage.newBuilder(descriptor)
            .setField(descriptor.findFieldByNumber(1), "abc")
            .setField(descriptor.findFieldByNumber(2), 42)
            .build()
            .toByteArray();

        byte[] written = mapper.writeValueAsBytes(
            Argument.of(FieldPositionModels.ByOrder.class), new FieldPositionModels.ByOrder("abc", 42));

        assertArrayEquals(expected, written);
        assertEquals(new FieldPositionModels.ByOrder("abc", 42),
            mapper.readValue(expected, Argument.of(FieldPositionModels.ByOrder.class)));
    }
}
