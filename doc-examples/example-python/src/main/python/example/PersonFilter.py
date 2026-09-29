from jakarta.inject import Named, Singleton
from micronaut.serde import PropertyFilter, Serializer
from micronaut.serde.Serializer import EncoderContext

from example.Person import Person


@Singleton
@Named("person-filter")  # <1>
class PersonFilter(PropertyFilter):
    def shouldInclude(
        self,
        encoder_context: EncoderContext,
        property_serializer: Serializer,
        bean: object,
        property_name: str,
        property_value: object,
    ) -> bool:
        if isinstance(bean, Person):  # <2>
            if property_name == "name":
                return bean.preferredName is None
            elif property_name == "preferredName":
                return bean.preferredName is not None
        return True
