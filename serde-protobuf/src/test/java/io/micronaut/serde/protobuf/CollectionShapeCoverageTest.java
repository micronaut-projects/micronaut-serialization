package io.micronaut.serde.protobuf;

import com.google.protobuf.ByteString;
import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A repeated field is a repeated field whatever Java collection stands in for it. Every shape here
 * is checked against the same reference message built by protobuf-java, which is a stronger claim
 * than the shapes merely agreeing with each other.
 */
class CollectionShapeCoverageTest {

    private static final List<String> TEXTS = List.of("alpha", "beta");
    private static final List<Integer> WHOLES = List.of(1, -2, 300);
    private static final List<Double> WIDES = List.of(1.5, -2.5);
    private static final List<Boolean> FLAGS = List.of(true, false);
    private static final List<byte[]> BLOBS = List.of(new byte[]{1, 2}, new byte[]{3});
    private static final List<Address> ADDRESSES = List.of(new Address("s1", "c1"), new Address("s2", "c2"));

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

    private static byte[] reference(boolean withBlobs) {
        Descriptors.Descriptor shapes = ProtoReference.SHAPES;
        Descriptors.Descriptor address = ProtoReference.ADDRESS;
        DynamicMessage.Builder builder = DynamicMessage.newBuilder(shapes)
            .setField(shapes.findFieldByNumber(1), TEXTS)
            .setField(shapes.findFieldByNumber(2), WHOLES)
            .setField(shapes.findFieldByNumber(3), WIDES)
            .setField(shapes.findFieldByNumber(4), FLAGS)
            .setField(shapes.findFieldByNumber(6), ADDRESSES.stream()
                .map(a -> DynamicMessage.newBuilder(address)
                    .setField(address.findFieldByNumber(1), a.street())
                    .setField(address.findFieldByNumber(2), a.city())
                    .build())
                .toList());
        if (withBlobs) {
            builder.setField(shapes.findFieldByNumber(5), BLOBS.stream().map(ByteString::copyFrom).toList());
        }
        return builder.build().toByteArray();
    }

    private static <T> LinkedHashSet<T> ordered(List<T> values) {
        return new LinkedHashSet<>(values);
    }

    @Test
    void aListEncodesAsTheReferenceMessage() throws Exception {
        var value = new ShapeModels.AsList(TEXTS, WHOLES, WIDES, FLAGS, BLOBS, ADDRESSES);
        assertArrayEquals(reference(true), mapper.writeValueAsBytes(Argument.of(ShapeModels.AsList.class), value));
    }

    @Test
    void aSetEncodesAsTheReferenceMessage() throws Exception {
        var value = new ShapeModels.AsSet(ordered(TEXTS), ordered(WHOLES), ordered(WIDES), ordered(FLAGS),
            ordered(BLOBS), ordered(ADDRESSES));
        assertArrayEquals(reference(true), mapper.writeValueAsBytes(Argument.of(ShapeModels.AsSet.class), value));
    }

    @Test
    void aCollectionEncodesAsTheReferenceMessage() throws Exception {
        var value = new ShapeModels.AsCollection(TEXTS, WHOLES, WIDES, FLAGS, BLOBS, ADDRESSES);
        assertArrayEquals(reference(true), mapper.writeValueAsBytes(Argument.of(ShapeModels.AsCollection.class), value));
    }

    @Test
    void anIterableEncodesAsTheReferenceMessage() throws Exception {
        var value = new ShapeModels.AsIterable(TEXTS, WHOLES, WIDES, FLAGS, BLOBS, ADDRESSES);
        assertArrayEquals(reference(true), mapper.writeValueAsBytes(Argument.of(ShapeModels.AsIterable.class), value));
    }

    @Test
    void arraysEncodeAsTheReferenceMessage() throws Exception {
        var value = new ShapeModels.AsArray(
            TEXTS.toArray(String[]::new),
            WHOLES.stream().mapToInt(Integer::intValue).toArray(),
            WIDES.stream().mapToDouble(Double::doubleValue).toArray(),
            new boolean[]{true, false},
            ADDRESSES.toArray(Address[]::new));
        assertArrayEquals(reference(false), mapper.writeValueAsBytes(Argument.of(ShapeModels.AsArray.class), value));
    }

    @Test
    void aListReadsBackFromTheReferenceMessage() throws Exception {
        ShapeModels.AsList value = mapper.readValue(reference(true), Argument.of(ShapeModels.AsList.class));

        assertEquals(TEXTS, value.texts());
        assertEquals(WHOLES, value.wholes());
        assertEquals(WIDES, value.wides());
        assertEquals(FLAGS, value.flags());
        assertEquals(ADDRESSES, value.addresses());
        assertEquals(BLOBS.size(), value.blobs().size());
        for (int i = 0; i < BLOBS.size(); i++) {
            assertArrayEquals(BLOBS.get(i), value.blobs().get(i), "blob " + i);
        }
    }

    @Test
    void aSetReadsBackFromTheReferenceMessage() throws Exception {
        // this asserted a ClassCastException until Set deserialization was fixed upstream
        ShapeModels.AsSet value = mapper.readValue(reference(true), Argument.of(ShapeModels.AsSet.class));

        assertEquals(ordered(TEXTS), value.texts());
        assertEquals(ordered(WHOLES), value.wholes());
        assertEquals(ordered(WIDES), value.wides());
        assertEquals(ordered(FLAGS), value.flags());
        assertEquals(ordered(ADDRESSES), value.addresses());
        assertEquals(BLOBS.size(), value.blobs().size());
    }

    @Test
    void arraysReadBackFromTheReferenceMessage() throws Exception {
        ShapeModels.AsArray value = mapper.readValue(reference(false), Argument.of(ShapeModels.AsArray.class));

        assertArrayEquals(TEXTS.toArray(String[]::new), value.texts());
        assertArrayEquals(new int[]{1, -2, 300}, value.wholes());
        assertArrayEquals(new double[]{1.5, -2.5}, value.wides());
        assertArrayEquals(new boolean[]{true, false}, value.flags());
        assertArrayEquals(ADDRESSES.toArray(Address[]::new), value.addresses());
    }

    @Test
    void theArrayShapesThatHaveNoReferenceFieldStillRoundTrip() throws Exception {
        var value = new ShapeModels.OtherArrays(
            new long[]{1L, -2L, Long.MAX_VALUE},
            new float[]{1.5f, -2.5f},
            new short[]{1, -2},
            new char[]{'a', 'z'},
            new byte[]{7, 8, 9},
            new Integer[]{10, 20},
            new java.math.BigInteger[]{java.math.BigInteger.ONE, java.math.BigInteger.valueOf(-99)});

        ShapeModels.OtherArrays back = mapper.readValue(
            mapper.writeValueAsBytes(Argument.of(ShapeModels.OtherArrays.class), value),
            Argument.of(ShapeModels.OtherArrays.class));

        assertArrayEquals(value.bigs(), back.bigs());
        assertArrayEquals(value.singles(), back.singles());
        assertArrayEquals(value.smalls(), back.smalls());
        assertArrayEquals(value.letters(), back.letters());
        assertArrayEquals(value.blob(), back.blob());
        assertArrayEquals(value.boxedWholes(), back.boxedWholes());
        assertArrayEquals(value.bigNumbers(), back.bigNumbers());
    }

    @Test
    void anEmptyCollectionIsAbsentFromThePayload() throws Exception {
        var value = new ShapeModels.EmptyCollections(List.of(), List.of(), List.of());

        byte[] payload = mapper.writeValueAsBytes(Argument.of(ShapeModels.EmptyCollections.class), value);

        // proto3 has no way to distinguish an empty repeated field from an absent one
        assertEquals(0, payload.length);
        ShapeModels.EmptyCollections back = mapper.readValue(payload, Argument.of(ShapeModels.EmptyCollections.class));
        assertEquals(new ArrayList<>(), orEmpty(back.texts()));
        assertEquals(new ArrayList<>(), orEmpty(back.wholes()));
        assertEquals(new ArrayList<>(), orEmpty(back.addresses()));
    }

    @Test
    void nestedCollectionsAreRejectedWithAnExplanation() {
        var value = new ShapeModels.NestedCollections(List.of(List.of("a")));

        Exception e = assertThrows(Exception.class,
            () -> mapper.writeValueAsBytes(Argument.of(ShapeModels.NestedCollections.class), value));
        assertTrue(rootMessage(e).contains("no nested repeated fields"), rootMessage(e));
    }

    @Test
    void mapsAreRejectedWithAnExplanation() {
        var value = new ShapeModels.WithMap(Map.of("k", "v"));

        Exception e = assertThrows(Exception.class,
            () -> mapper.writeValueAsBytes(Argument.of(ShapeModels.WithMap.class), value));
        assertTrue(rootMessage(e).contains("No introspection found for [java.util.Map]"), rootMessage(e));
    }

    @Test
    void nestedByteArraysAreNotSupportedBySerdeItself() {
        var value = new ShapeModels.ByteMatrix(new byte[][]{{1, 2}, {3}});

        // not a protobuf limitation: the generated serializer picks the byte[] serializer for the
        // outer array, and writeValueToTree fails the same way without reaching an encoder
        Exception e = assertThrows(Exception.class,
            () -> mapper.writeValueAsBytes(Argument.of(ShapeModels.ByteMatrix.class), value));
        assertTrue(rootMessage(e).contains("cannot be cast to class [B"), rootMessage(e));
    }

    private static <T> List<T> orEmpty(List<T> value) {
        return value == null ? List.of() : value;
    }

    private static String rootMessage(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return String.valueOf(cause.getMessage());
    }
}
