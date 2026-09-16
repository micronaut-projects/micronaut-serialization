from abc import ABC, abstractmethod
from typing import Annotated

from com.fasterxml.jackson.annotation import JsonProperty


class ProductMixin(ABC):
    @abstractmethod
    def getName(self) -> Annotated[str, JsonProperty("p_name")]:
        pass

    @abstractmethod
    def getQuantity(self) -> Annotated[int, JsonProperty("p_quantity")]:
        pass
