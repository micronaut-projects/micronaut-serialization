package io.micronaut.serde.support.deserializers;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;

import java.io.IOException;
import java.util.ArrayList;

final class StringListDeserializer implements Deserializer<ArrayList<String>> {

    @Override
    public ArrayList<String> deserialize(Decoder decoder, DecoderContext context, Argument<? super ArrayList<String>> type) throws IOException {
        if (decoder.decodeNull()) {
            return null;
        }
        final Decoder arrayDecoder = decoder.decodeArray();
        ArrayList<String> collection = new ArrayList<>();
        while (arrayDecoder.hasNextArrayValue()) {
            collection.add(arrayDecoder.decodeString());
        }
        arrayDecoder.finishStructure();
        return collection;
    }

    @Override
    public ArrayList<String> getDefaultValue(DecoderContext context, Argument<? super ArrayList<String>> type) {
        return new ArrayList<>();
    }

    @Override
    public boolean allowNull() {
        return true;
    }

}
