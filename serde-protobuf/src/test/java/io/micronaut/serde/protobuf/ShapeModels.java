package io.micronaut.serde.protobuf;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.protobuf.annotation.ProtoField;

import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The same six repeated fields expressed as lists, sets, other collection interfaces and arrays,
 * so that each shape can be checked to produce the same wire bytes as the others.
 */
final class ShapeModels {

    private ShapeModels() {
    }

    @Serdeable
    record AsList(List<String> texts,
                  List<Integer> wholes,
                  List<Double> wides,
                  List<Boolean> flags,
                  List<byte[]> blobs,
                  List<Address> addresses) {
    }

    @Serdeable
    record AsSet(Set<String> texts,
                 Set<Integer> wholes,
                 Set<Double> wides,
                 Set<Boolean> flags,
                 Set<byte[]> blobs,
                 Set<Address> addresses) {
    }

    @Serdeable
    record AsCollection(Collection<String> texts,
                        Collection<Integer> wholes,
                        Collection<Double> wides,
                        Collection<Boolean> flags,
                        Collection<byte[]> blobs,
                        Collection<Address> addresses) {
    }

    @Serdeable
    record AsIterable(Iterable<String> texts,
                      Iterable<Integer> wholes,
                      Iterable<Double> wides,
                      Iterable<Boolean> flags,
                      Iterable<byte[]> blobs,
                      Iterable<Address> addresses) {
    }

    /**
     * Positions are explicit here so the record still lines up with the reference message despite
     * having no {@code blobs}: a {@code byte[][]} property cannot be serialized by Micronaut
     * Serialization at all, which {@link CollectionShapeCoverageTest} records separately.
     */
    @Serdeable
    record AsArray(@ProtoField(1) String[] texts,
                   @ProtoField(2) int[] wholes,
                   @ProtoField(3) double[] wides,
                   @ProtoField(4) boolean[] flags,
                   @ProtoField(6) Address[] addresses) {
    }

    @Serdeable
    record ByteMatrix(byte[][] blobs) {
    }

    /**
     * The array shapes that do not appear in the reference message, kept separate so the shared
     * cross-check stays aligned with it.
     */
    @Serdeable
    record OtherArrays(long[] bigs,
                       float[] singles,
                       short[] smalls,
                       char[] letters,
                       byte[] blob,
                       Integer[] boxedWholes,
                       BigInteger[] bigNumbers) {
    }

    @Serdeable
    record EmptyCollections(List<String> texts, List<Integer> wholes, List<Address> addresses) {
    }

    @Serdeable
    record NestedCollections(List<List<String>> nested) {
    }

    @Serdeable
    record WithMap(Map<String, String> entries) {
    }
}
