package example;

import io.micronaut.context.ApplicationContext;
import io.micronaut.serde.jsonb.JsonbConfiguration;
import jakarta.json.bind.Jsonb;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class JsonbExtensionTest {
    @Test
    void jsonbExtensionBeansAreRegisteredInPriorityOrder() throws Exception {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "spec.name", "jsonb-extension-beans",
            JsonbConfiguration.REFLECTION, JsonbConfiguration.Reflection.AUTO
        ))) {
            Jsonb jsonb = context.getBean(Jsonb.class);

            assertEquals("\"#ff0000\"", jsonb.toJson(new Color("ff0000")));
            assertEquals("00ff00", jsonb.fromJson("\"#00ff00\"", Color.class).getValue());
            assertEquals("\"12 mi\"", jsonb.toJson(new Miles(12)));
            assertEquals(15, jsonb.fromJson("\"15 mi\"", Miles.class).getValue());
        }
    }

    @Test
    void jsonbExtensionsCanBeRegisteredProgrammatically() throws Exception {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "spec.name", "jsonb-programmatic-config",
            JsonbConfiguration.REFLECTION, JsonbConfiguration.Reflection.AUTO
        ))) {
            Jsonb jsonb = context.getBean(Jsonb.class);

            assertEquals("\"code:A1\"", jsonb.toJson(new ProgrammaticCode("A1")));
            assertEquals("B2", jsonb.fromJson("\"code:B2\"", ProgrammaticCode.class).getValue());
        }
    }
}
