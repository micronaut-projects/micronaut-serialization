package example;

import io.micronaut.context.annotation.Requires;
import jakarta.annotation.Priority;
import jakarta.inject.Singleton;
import jakarta.json.bind.adapter.JsonbAdapter;

@Singleton
@Requires(property = "spec.name", value = "jsonb-extension-beans")
@Priority(10)
public final class MilesAdapter implements JsonbAdapter<Miles, String> {
    @Override
    public String adaptToJson(Miles obj) {
        return obj.getValue() + " mi";
    }

    @Override
    public Miles adaptFromJson(String obj) {
        return new Miles(Integer.parseInt(obj.replace(" mi", "")));
    }
}
