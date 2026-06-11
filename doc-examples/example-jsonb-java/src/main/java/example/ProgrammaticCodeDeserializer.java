package example;

import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.stream.JsonParser;

import java.lang.reflect.Type;

public final class ProgrammaticCodeDeserializer implements JsonbDeserializer<ProgrammaticCode> {
    @Override
    public ProgrammaticCode deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
        while (parser.hasNext()) {
            if (parser.next() == JsonParser.Event.VALUE_STRING) {
                return new ProgrammaticCode(parser.getString().substring(5));
            }
        }
        throw new IllegalStateException("Expected a JSON string");
    }
}
