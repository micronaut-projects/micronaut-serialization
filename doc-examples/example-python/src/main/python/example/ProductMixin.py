from abc import ABC, abstractmethod

from com.fasterxml.jackson.annotation import JsonProperty


class ProductMixin(ABC):
    @JsonProperty("p_name")
    @abstractmethod
    def getName(self) -> str:
        pass

    @JsonProperty("p_quantity")
    @abstractmethod
    def getQuantity(self) -> int:
        pass
