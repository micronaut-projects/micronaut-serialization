package io.micronaut.serde.support.util;

import io.micronaut.core.type.Argument;
import io.micronaut.json.tree.JsonNode;
import io.micronaut.serde.Encoder;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;

public class JsonNodeToStringUtil {

    public static String toString(JsonNode node) throws IOException {
        if (node == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder();
        Encoder encoder = new SimpleToStringValueEncoder(builder);
        encode(encoder, node);
        return builder.toString();
    }

    public static void encode(Encoder encoder, JsonNode node) throws IOException {
        if (node.isObject()) {
            try (Encoder objectEncoder = encoder.encodeObject(Argument.OBJECT_ARGUMENT)) {
                for (Map.Entry<String, JsonNode> e : node.entries()) {
                    objectEncoder.encodeKey(e.getKey());
                    encode(objectEncoder, e.getValue());
                }
            }
            return;
        }
        if (node.isArray()) {
            try (Encoder arrayEncoder = encoder.encodeArray(Argument.OBJECT_ARGUMENT)) {
                for (JsonNode value : node.values()) {
                    encode(arrayEncoder, value);
                }
            }
            return;
        }
        if (node.isNull()) {
            encoder.encodeNull();
            return;
        }
        if (node.isString()) {
            encoder.encodeString(node.getStringValue());
            return;
        }
        if (node.isNumber()) {
            Number numberValue = node.getNumberValue();
            if (numberValue instanceof Integer integer) {
                encoder.encodeInt(integer);
                return;
            }
            if (numberValue instanceof Double aDouble) {
                encoder.encodeDouble(aDouble);
                return;
            }
        }
        if (node.isBoolean()) {
            encoder.encodeBoolean(node.getBooleanValue());
            return;
        }
        throw new IllegalStateException("Unsupported node type: " + node.getClass().getSimpleName());
    }

    private static final class SimpleToStringObjectEncoder extends AbstractSimpleToStringEncoder implements Encoder {

        private boolean hasPreviousItem;
        private SimpleToStringObjectEncoder(StringBuilder builder) {
            super(builder);
            builder.append("{");
        }

        @Override
        protected void beforeEncodeValue() {
        }

        @Override
        public void encodeKey(String key) {
            if (hasPreviousItem) {
                builder.append(", ");
            } else {
                hasPreviousItem = true;
            }
            builder.append("\"").append(key).append("\": ");
        }

        @Override
        public void finishStructure() {
            builder.append("}");
        }
    }

    private static final class SimpleToStringArrayEncoder extends AbstractSimpleToStringEncoder implements Encoder {

        private boolean hasPreviousItem;

        private SimpleToStringArrayEncoder(StringBuilder builder) {
            super(builder);
            builder.append("[");
        }

        @Override
        protected void beforeEncodeValue() {
            if (hasPreviousItem) {
                builder.append(", ");
            } else {
                hasPreviousItem = true;
            }
        }

        @Override
        public void encodeKey(String key) {
            throw new IllegalStateException("Unsupported encodeKey for the array encoder");
        }

        @Override
        public void finishStructure() {
            builder.append("]");
        }
    }

    private static final class SimpleToStringValueEncoder extends AbstractSimpleToStringEncoder implements Encoder {

        private SimpleToStringValueEncoder(StringBuilder builder) {
            super(builder);
        }

        @Override
        protected void beforeEncodeValue() {
        }

        @Override
        public void encodeKey(String key) {
            throw new IllegalStateException("Unsupported encodeKey for the array encoder");
        }

        @Override
        public void finishStructure() {
        }
    }

    private static abstract class AbstractSimpleToStringEncoder implements Encoder {

        protected final StringBuilder builder;

        private AbstractSimpleToStringEncoder(StringBuilder builder) {
            this.builder = builder;
        }

        protected abstract void beforeEncodeValue();

        @Override
        public Encoder encodeArray(Argument<?> type) {
            beforeEncodeValue();
            return new SimpleToStringArrayEncoder(builder);
        }

        @Override
        public Encoder encodeObject(Argument<?> type) {
            beforeEncodeValue();
            return new SimpleToStringObjectEncoder(builder);
        }

        @Override
        public void encodeString(String value) {
            beforeEncodeValue();
            builder.append("\"").append(value).append("\"");
        }

        @Override
        public void encodeBoolean(boolean value) {
            beforeEncodeValue();
            builder.append(value);
        }

        @Override
        public void encodeByte(byte value) {
            beforeEncodeValue();
            builder.append(value);
        }

        @Override
        public void encodeShort(short value) {
            beforeEncodeValue();
            builder.append(value);
        }

        @Override
        public void encodeChar(char value) {
            beforeEncodeValue();
            builder.append(value);
        }

        @Override
        public void encodeInt(int value) {
            beforeEncodeValue();
            builder.append(value);
        }

        @Override
        public void encodeLong(long value) {
            beforeEncodeValue();
            builder.append(value);
        }

        @Override
        public void encodeFloat(float value) {
            beforeEncodeValue();
            builder.append(value);
        }

        @Override
        public void encodeDouble(double value) {
            beforeEncodeValue();
            builder.append(value);
        }

        @Override
        public void encodeBigInteger(BigInteger value) {
            beforeEncodeValue();
            builder.append(value);
        }

        @Override
        public void encodeBigDecimal(BigDecimal value) {
            beforeEncodeValue();
            builder.append(value);
        }

        @Override
        public void encodeNull() {
            beforeEncodeValue();
            builder.append("null");
        }
    }

}
