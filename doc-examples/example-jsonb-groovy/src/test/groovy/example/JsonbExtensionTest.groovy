package example

import io.micronaut.context.ApplicationContext
import io.micronaut.serde.jsonb.JsonbConfiguration
import jakarta.json.bind.Jsonb
import spock.lang.Specification

class JsonbExtensionTest extends Specification {

    void "jsonb extension beans are registered in priority order"() {
        given:
        ApplicationContext context = ApplicationContext.run([
            "spec.name": "jsonb-extension-beans",
            (JsonbConfiguration.REFLECTION): JsonbConfiguration.Reflection.AUTO
        ])
        Jsonb jsonb = context.getBean(Jsonb)

        expect:
        jsonb.toJson(new Color("ff0000")) == '"#ff0000"'
        jsonb.fromJson('"#00ff00"', Color).value == "00ff00"
        jsonb.toJson(new Miles(12)) == '"12 mi"'
        jsonb.fromJson('"15 mi"', Miles).value == 15

        cleanup:
        context.close()
    }

    void "jsonb extensions can be registered programmatically"() {
        given:
        ApplicationContext context = ApplicationContext.run([
            "spec.name": "jsonb-programmatic-config",
            (JsonbConfiguration.REFLECTION): JsonbConfiguration.Reflection.AUTO
        ])
        Jsonb jsonb = context.getBean(Jsonb)

        expect:
        jsonb.toJson(new ProgrammaticCode("A1")) == '"code:A1"'
        jsonb.fromJson('"code:B2"', ProgrammaticCode).value == "B2"

        cleanup:
        context.close()
    }
}
