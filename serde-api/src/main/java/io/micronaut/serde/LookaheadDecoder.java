package io.micronaut.serde;

import io.micronaut.core.type.Argument;

import java.io.IOException;

public interface LookaheadDecoder extends Decoder {

    @Override
    default LookaheadDecoder decodeObject() throws IOException {
        return decodeObject(Argument.OBJECT_ARGUMENT);
    }

    @Override
    LookaheadDecoder decodeObject(Argument<?> type) throws IOException;

    @Override
    default LookaheadDecoder decodeArray() throws IOException {
        return decodeArray(Argument.OBJECT_ARGUMENT);
    }

    @Override
    LookaheadDecoder decodeArray(Argument<?> type) throws IOException;

    TokenType lookahead() throws IOException;

    /**
     * The token type.
     */
    enum TokenType {
        /**
         * Start of an array.
         */
        START_ARRAY,
        /**
         * End of an array.
         */
        END_ARRAY,
        /**
         * Start of an object.
         */
        START_OBJECT,
        /**
         * End of an object.
         */
        END_OBJECT,
        /**
         * A key.
         */
        KEY,
        /**
         * A number.
         */
        NUMBER,
        /**
         * A string.
         */
        STRING,
        /**
         * A boolean.
         */
        BOOLEAN,
        /**
         * A {@code null} value.
         */
        NULL,
        /**
         * Any other token.
         */
        OTHER
    }

}
