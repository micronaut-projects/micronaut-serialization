package io.micronaut.serde.protobuf;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.protobuf.annotation.ProtoField;
import io.micronaut.serde.protobuf.annotation.ProtoType;

/**
 * The ways a field number can be arrived at: written as the annotation's value, written as its
 * position, or left to the property's place in the message.
 */
final class FieldPositionModels {

    private FieldPositionModels() {
    }

    @Serdeable
    record ByValue(@ProtoField(3) String name, @ProtoField(7) int count) {
    }

    @Serdeable
    record ByPosition(@ProtoField(position = 3) String name, @ProtoField(position = 7) int count) {
    }

    @Serdeable
    record ByOrder(String name, int count) {
    }

    @Serdeable
    record TypedButUnpositioned(@ProtoField(type = ProtoType.SINT32) int temperature, String note) {
    }

    @Serdeable
    record Mixed(@ProtoField(10) String first, String second, @ProtoField(position = 11) String third, String fourth) {
    }

    /**
     * A classic getter/setter bean rather than a record, to check that ordering follows the
     * introspection for those too.
     */
    @Serdeable
    static class MutableBean {
        private String alpha;
        private int beta;
        private boolean gamma;

        public String getAlpha() {
            return alpha;
        }

        public void setAlpha(String alpha) {
            this.alpha = alpha;
        }

        public int getBeta() {
            return beta;
        }

        public void setBeta(int beta) {
            this.beta = beta;
        }

        public boolean isGamma() {
            return gamma;
        }

        public void setGamma(boolean gamma) {
            this.gamma = gamma;
        }
    }
}
