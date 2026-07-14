package example;

import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.bind.serializer.SerializationContext;
import jakarta.json.stream.JsonGenerator;

public final class ProgrammaticCodeSerializer implements JsonbSerializer<ProgrammaticCode> {
    @Override
    public void serialize(ProgrammaticCode obj, JsonGenerator generator, SerializationContext ctx) {
        generator.write("code:" + obj.getValue());
    }
}
