package example

import io.micronaut.context.annotation.Requires
import jakarta.annotation.Priority
import jakarta.inject.Singleton
import jakarta.json.bind.adapter.JsonbAdapter

@Singleton
@Requires(property = "spec.name", value = "jsonb-extension-beans")
@Priority(10)
class MilesAdapter : JsonbAdapter<Miles, String> {
    override fun adaptToJson(obj: Miles): String = "${obj.value} mi"

    override fun adaptFromJson(obj: String): Miles = Miles(obj.replace(" mi", "").toInt())
}
