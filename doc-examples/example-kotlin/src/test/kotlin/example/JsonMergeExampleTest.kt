package example

import io.micronaut.core.type.Argument
import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

@MicronautTest
class JsonMergeExampleTest {

    @Test
    fun testMergeNestedReleaseConfiguration(objectMapper: ObjectMapper) {
        val release = ReleaseConfiguration()
        release.service = "checkout"
        release.owner = "platform"

        val window = ReleaseConfiguration.DeploymentWindow()
        window.day = "Friday"
        window.timeZone = "UTC"
        release.deploymentWindow = window

        objectMapper.updateValue(
            release,
            Argument.of(ReleaseConfiguration::class.java),
            """
            {
              "owner": "growth",
              "deploymentWindow": {
                "day": "Tuesday"
              }
            }
            """.trimIndent().toByteArray(StandardCharsets.UTF_8)
        )

        assertEquals("growth", release.owner)
        assertSame(window, release.deploymentWindow)
        assertEquals("Tuesday", release.deploymentWindow.day)
        assertEquals("UTC", release.deploymentWindow.timeZone)
    }

    @Test
    fun testMergeReleaseLabels(objectMapper: ObjectMapper) {
        val release = ReleaseConfiguration()
        release.labels = linkedMapOf(
            "environment" to "production",
            "region" to "us-east"
        )

        objectMapper.updateValue(
            release,
            Argument.of(ReleaseConfiguration::class.java),
            """
            {
              "labels": {
                "version": "2026.06",
                "region": "eu-west"
              }
            }
            """.trimIndent().toByteArray(StandardCharsets.UTF_8)
        )

        assertEquals("production", release.labels["environment"])
        assertEquals("eu-west", release.labels["region"])
        assertEquals("2026.06", release.labels["version"])
    }
}
