package io.micronaut.serde;

import java.io.IOException;

public interface LookaheadDecoder extends Decoder {

    @Override
    LookaheadDecoder decodeObject() throws IOException;

    @Override
    LookaheadDecoder decodeArray() throws IOException;

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
