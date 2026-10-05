from typing import Annotated

from jakarta.inject import Inject
from micronaut.core.type import Argument
from micronaut.serde import ObjectMapper
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from example.ReleaseConfiguration import DeploymentWindow, ReleaseConfiguration


@MicronautTest
class JsonMergeExampleTest:
    object_mapper: Annotated[ObjectMapper, Inject]

    @Test
    def test_merge_nested_release_configuration(self):
        release = ReleaseConfiguration()
        release.service = "checkout"
        release.owner = "platform"

        window = DeploymentWindow()
        window.day = "Friday"
        window.timeZone = "UTC"
        release.deploymentWindow = window

        release = self.object_mapper.updateValue(
            release,
            Argument.of(ReleaseConfiguration),
            {
                "owner": "growth",
                "deploymentWindow": {
                    "day": "Tuesday"
                },
            },
        )

        assert release.owner == "growth"
        assert release.deploymentWindow.day == "Tuesday"
        assert release.deploymentWindow.timeZone == "UTC"

    @Test
    def test_merge_release_labels(self):
        release = ReleaseConfiguration()
        release.labels = {
            "environment": "production",
            "region": "us-east",
        }

        release = self.object_mapper.updateValue(
            release,
            Argument.of(ReleaseConfiguration),
            {
                "labels": {
                    "version": "2026.06",
                    "region": "eu-west",
                },
            },
        )

        assert release.labels["environment"] == "production"
        assert release.labels["region"] == "eu-west"
        assert release.labels["version"] == "2026.06"
