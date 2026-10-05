package example

import io.micronaut.core.type.Argument
import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import java.nio.charset.StandardCharsets

@MicronautTest
class JsonMergeExampleTest extends Specification {
    @Inject ObjectMapper objectMapper

    void "test merge nested release configuration"() {
        given:
        ReleaseConfiguration release = new ReleaseConfiguration()
        release.service = "checkout"
        release.owner = "platform"

        ReleaseConfiguration.DeploymentWindow window = new ReleaseConfiguration.DeploymentWindow()
        window.day = "Friday"
        window.timeZone = "UTC"
        release.deploymentWindow = window

        when:
        objectMapper.updateValue(
            release,
            Argument.of(ReleaseConfiguration),
            '''
            {
              "owner": "growth",
              "deploymentWindow": {
                "day": "Tuesday"
              }
            }
            '''.getBytes(StandardCharsets.UTF_8)
        )

        then:
        release.owner == "growth"
        release.deploymentWindow.is(window)
        release.deploymentWindow.day == "Tuesday"
        release.deploymentWindow.timeZone == "UTC"
    }

    void "test merge release labels"() {
        given:
        ReleaseConfiguration release = new ReleaseConfiguration()
        release.labels = new LinkedHashMap<>(Map.of(
            "environment", "production",
            "region", "us-east"
        ))

        when:
        objectMapper.updateValue(
            release,
            Argument.of(ReleaseConfiguration),
            '''
            {
              "labels": {
                "version": "2026.06",
                "region": "eu-west"
              }
            }
            '''.getBytes(StandardCharsets.UTF_8)
        )

        then:
        release.labels.get("environment") == "production"
        release.labels.get("region") == "eu-west"
        release.labels.get("version") == "2026.06"
    }
}
