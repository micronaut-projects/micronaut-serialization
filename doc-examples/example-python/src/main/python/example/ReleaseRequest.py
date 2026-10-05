from dataclasses import dataclass
from typing import Annotated

from com.fasterxml.jackson.annotation import JsonProperty
from micronaut.context.annotation import Executable
from micronaut.core.annotation import Introspected
from micronaut.serde.annotation import Serdeable


class ReleaseRequestBuilder:
    # @Executable exposes the builder methods to the generated Java code that drives the builder
    def __init__(self):
        self._service = None
        self._owner = None
        self._notes = None

    @Executable
    def service(self, service: str) -> "ReleaseRequestBuilder":
        self._service = service
        return self

    @Executable
    def owner(self, owner: str) -> "ReleaseRequestBuilder":
        self._owner = owner
        return self

    @Executable
    def notes(self, notes: str) -> "ReleaseRequestBuilder":
        self._notes = notes
        return self

    @Executable
    def build(self) -> "ReleaseRequest":
        return ReleaseRequest(self._service, self._owner, self._notes)


@Serdeable
@Introspected(builder=Introspected.IntrospectionBuilder(builderClass=ReleaseRequestBuilder))
@dataclass(frozen=True)
class ReleaseRequest:
    service: Annotated[str, JsonProperty(required=True)]  # <1>
    owner: Annotated[str, JsonProperty(defaultValue="platform")]  # <2>
    notes: str | None = None  # <3>
