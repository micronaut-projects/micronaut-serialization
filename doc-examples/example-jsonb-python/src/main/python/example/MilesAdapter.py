from jakarta.annotation import Priority
from jakarta.inject import Singleton
from jakarta.json.bind.adapter import JsonbAdapter
from micronaut.context.annotation import Requires

from example.Miles import Miles


@Singleton
@Requires(property="spec.name", value="jsonb-extension-beans")
@Priority(10)
class MilesAdapter(JsonbAdapter[Miles, str]):
    def adaptToJson(self, obj: Miles) -> str:
        return f"{obj.value} mi"

    def adaptFromJson(self, obj: str) -> Miles:
        return Miles(int(obj.replace(" mi", "")))
