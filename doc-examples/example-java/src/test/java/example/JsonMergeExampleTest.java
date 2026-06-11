package example;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

@MicronautTest
public class JsonMergeExampleTest {

    @Test
    void testMergeNestedReleaseConfiguration(ObjectMapper objectMapper) throws IOException {
        ReleaseConfiguration release = new ReleaseConfiguration();
        release.setService("checkout");
        release.setOwner("platform");

        ReleaseConfiguration.DeploymentWindow window = new ReleaseConfiguration.DeploymentWindow();
        window.setDay("Friday");
        window.setTimeZone("UTC");
        release.setDeploymentWindow(window);

        objectMapper.updateValue(
            release,
            Argument.of(ReleaseConfiguration.class),
            """
            {
              "owner": "growth",
              "deploymentWindow": {
                "day": "Tuesday"
              }
            }
            """.getBytes(StandardCharsets.UTF_8)
        );

        assertEquals("growth", release.getOwner());
        assertSame(window, release.getDeploymentWindow());
        assertEquals("Tuesday", release.getDeploymentWindow().getDay());
        assertEquals("UTC", release.getDeploymentWindow().getTimeZone());
    }

    @Test
    void testMergeReleaseLabels(ObjectMapper objectMapper) throws IOException {
        ReleaseConfiguration release = new ReleaseConfiguration();
        release.setLabels(new java.util.LinkedHashMap<>(Map.of(
            "environment", "production",
            "region", "us-east"
        )));

        objectMapper.updateValue(
            release,
            Argument.of(ReleaseConfiguration.class),
            """
            {
              "labels": {
                "version": "2026.06",
                "region": "eu-west"
              }
            }
            """.getBytes(StandardCharsets.UTF_8)
        );

        assertEquals("production", release.getLabels().get("environment"));
        assertEquals("eu-west", release.getLabels().get("region"));
        assertEquals("2026.06", release.getLabels().get("version"));
    }
}
