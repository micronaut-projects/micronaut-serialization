from typing import Annotated

from com.fasterxml.jackson.annotation import JsonMerge
from micronaut.serde.annotation import Serdeable


@Serdeable
class DeploymentWindow:
    day: str
    timeZone: str

    def __init__(self):
        self.day = ""
        self.timeZone = ""


@Serdeable
class ReleaseConfiguration:
    service: str
    owner: str
    deploymentWindow: Annotated[DeploymentWindow, JsonMerge]
    labels: Annotated[dict[str, str], JsonMerge]

    def __init__(self):
        self.service = ""
        self.owner = ""
        self.deploymentWindow = DeploymentWindow()
        self.labels = {}
