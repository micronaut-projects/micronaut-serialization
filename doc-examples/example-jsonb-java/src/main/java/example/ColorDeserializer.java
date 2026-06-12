package example;

import io.micronaut.context.annotation.Requires;
import jakarta.annotation.Priority;
import jakarta.inject.Singleton;
import jakarta.json.bind.serializer.DeserializationContext;
import jakarta.json.bind.serializer.JsonbDeserializer;
import jakarta.json.stream.JsonParser;

import java.lang.reflect.Type;

@Singleton
@Requires(property = "spec.name", value = "jsonb-extension-beans")
@Priority(10)
public final class ColorDeserializer implements JsonbDeserializer<Color> {
    @Override
    public Color deserialize(JsonParser parser, DeserializationContext ctx, Type rtType) {
        while (parser.hasNext()) {
            if (parser.next() == JsonParser.Event.VALUE_STRING) {
                return new Color(parser.getString().substring(1));
            }
        }
        throw new IllegalStateException("Expected a JSON string");
    }
}
