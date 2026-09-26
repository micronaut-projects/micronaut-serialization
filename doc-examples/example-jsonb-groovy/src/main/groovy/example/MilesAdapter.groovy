package example

import io.micronaut.context.annotation.Requires
import jakarta.annotation.Priority
import jakarta.inject.Singleton
import jakarta.json.bind.adapter.JsonbAdapter

@Singleton
@Requires(property = "spec.name", value = "jsonb-extension-beans")
@Priority(10)
final class MilesAdapter implements JsonbAdapter<Miles, String> {
    @Override
    String adaptToJson(Miles obj) {
        return obj.value + " mi"
    }

    @Override
    Miles adaptFromJson(String obj) {
        return new Miles(Integer.parseInt(obj.replace(" mi", "")))
    }
}
