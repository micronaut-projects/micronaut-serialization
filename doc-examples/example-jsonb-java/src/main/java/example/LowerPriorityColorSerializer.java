package example;

import io.micronaut.context.annotation.Requires;
import jakarta.annotation.Priority;
import jakarta.inject.Singleton;
import jakarta.json.bind.serializer.JsonbSerializer;
import jakarta.json.bind.serializer.SerializationContext;
import jakarta.json.stream.JsonGenerator;

@Singleton
@Requires(property = "spec.name", value = "jsonb-extension-beans")
@Priority(20)
public final class LowerPriorityColorSerializer implements JsonbSerializer<Color> {
    @Override
    public void serialize(Color obj, JsonGenerator generator, SerializationContext ctx) {
        generator.write("fallback-" + obj.getValue());
    }
}
