package io.micronaut.serde.protobuf;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.Descriptors;

/**
 * The reference schema, built as a real protobuf descriptor so that protobuf-java itself can encode
 * and decode the same messages. Equivalent to:
 *
 * <pre>
 * syntax = "proto3";
 * package test;
 * message Address {
 *   string street = 1;
 *   string city = 2;
 * }
 * message Person {
 *   string name = 1;
 *   int32 age = 2;
 *   Address address = 3;
 *   repeated string nicknames = 4;
 *   repeated int32 scores = 5;
 *   sint32 balance = 6;
 *   bool active = 7;
 *   double ratio = 8;
 * }
 * </pre>
 */
final class ProtoReference {

    static final Descriptors.Descriptor ADDRESS;
    static final Descriptors.Descriptor PERSON;
    static final Descriptors.Descriptor PERSON_UNPACKED;
    static final Descriptors.Descriptor TEAM;
    static final Descriptors.Descriptor ORG;
    static final Descriptors.Descriptor BLOB;
    static final Descriptors.Descriptor NUMBERS;

    private ProtoReference() {
    }

    static {
        DescriptorProtos.FileDescriptorProto file = DescriptorProtos.FileDescriptorProto.newBuilder()
            .setName("reference.proto")
            .setSyntax("proto3")
            .setPackage("test")
            .addMessageType(DescriptorProtos.DescriptorProto.newBuilder()
                .setName("Address")
                .addField(field("street", 1, DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING))
                .addField(field("city", 2, DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING)))
            .addMessageType(DescriptorProtos.DescriptorProto.newBuilder()
                .setName("Person")
                .addField(field("name", 1, DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING))
                .addField(field("age", 2, DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32))
                .addField(field("address", 3, DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
                    .setTypeName(".test.Address"))
                .addField(repeated("nicknames", 4, DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING))
                .addField(repeated("scores", 5, DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32))
                .addField(field("balance", 6, DescriptorProtos.FieldDescriptorProto.Type.TYPE_SINT32))
                .addField(field("active", 7, DescriptorProtos.FieldDescriptorProto.Type.TYPE_BOOL))
                .addField(field("ratio", 8, DescriptorProtos.FieldDescriptorProto.Type.TYPE_DOUBLE)))
            // the same message, but with the repeated scalar explicitly not packed, which older
            // producers and proto2 peers are entitled to emit
            .addMessageType(DescriptorProtos.DescriptorProto.newBuilder()
                .setName("PersonUnpacked")
                .addField(field("name", 1, DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING))
                .addField(field("age", 2, DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32))
                .addField(field("address", 3, DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
                    .setTypeName(".test.Address"))
                .addField(repeated("nicknames", 4, DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING))
                .addField(repeated("scores", 5, DescriptorProtos.FieldDescriptorProto.Type.TYPE_INT32)
                    .setOptions(DescriptorProtos.FieldOptions.newBuilder().setPacked(false)))
                .addField(field("balance", 6, DescriptorProtos.FieldDescriptorProto.Type.TYPE_SINT32))
                .addField(field("active", 7, DescriptorProtos.FieldDescriptorProto.Type.TYPE_BOOL))
                .addField(field("ratio", 8, DescriptorProtos.FieldDescriptorProto.Type.TYPE_DOUBLE)))
            .addMessageType(DescriptorProtos.DescriptorProto.newBuilder()
                .setName("Team")
                .addField(field("name", 1, DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING))
                .addField(repeated("members", 2, DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
                    .setTypeName(".test.Address")))
            .addMessageType(DescriptorProtos.DescriptorProto.newBuilder()
                .setName("Org")
                .addField(field("name", 1, DescriptorProtos.FieldDescriptorProto.Type.TYPE_STRING))
                .addField(repeated("teams", 2, DescriptorProtos.FieldDescriptorProto.Type.TYPE_MESSAGE)
                    .setTypeName(".test.Team")))
            .addMessageType(DescriptorProtos.DescriptorProto.newBuilder()
                .setName("Blob")
                .addField(field("data", 1, DescriptorProtos.FieldDescriptorProto.Type.TYPE_BYTES)))
            .addMessageType(DescriptorProtos.DescriptorProto.newBuilder()
                .setName("Numbers")
                .addField(field("fixedWidth", 1, DescriptorProtos.FieldDescriptorProto.Type.TYPE_FIXED32))
                .addField(field("signedFixedWidth", 2, DescriptorProtos.FieldDescriptorProto.Type.TYPE_SFIXED64))
                .addField(field("unsigned", 3, DescriptorProtos.FieldDescriptorProto.Type.TYPE_UINT32))
                .addField(field("zigZag", 4, DescriptorProtos.FieldDescriptorProto.Type.TYPE_SINT64))
                .addField(field("ratio", 5, DescriptorProtos.FieldDescriptorProto.Type.TYPE_FLOAT)))
            .build();

        try {
            Descriptors.FileDescriptor descriptor =
                Descriptors.FileDescriptor.buildFrom(file, new Descriptors.FileDescriptor[0]);
            ADDRESS = descriptor.findMessageTypeByName("Address");
            PERSON = descriptor.findMessageTypeByName("Person");
            PERSON_UNPACKED = descriptor.findMessageTypeByName("PersonUnpacked");
            TEAM = descriptor.findMessageTypeByName("Team");
            ORG = descriptor.findMessageTypeByName("Org");
            BLOB = descriptor.findMessageTypeByName("Blob");
            NUMBERS = descriptor.findMessageTypeByName("Numbers");
        } catch (Descriptors.DescriptorValidationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static DescriptorProtos.FieldDescriptorProto.Builder field(
        String name, int number, DescriptorProtos.FieldDescriptorProto.Type type) {
        return DescriptorProtos.FieldDescriptorProto.newBuilder()
            .setName(name)
            .setNumber(number)
            .setType(type)
            .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_OPTIONAL);
    }

    private static DescriptorProtos.FieldDescriptorProto.Builder repeated(
        String name, int number, DescriptorProtos.FieldDescriptorProto.Type type) {
        return DescriptorProtos.FieldDescriptorProto.newBuilder()
            .setName(name)
            .setNumber(number)
            .setType(type)
            .setLabel(DescriptorProtos.FieldDescriptorProto.Label.LABEL_REPEATED);
    }
}
