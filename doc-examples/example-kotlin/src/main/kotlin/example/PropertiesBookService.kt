package example

import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.properties.PropertiesMapper
import jakarta.inject.Named
import jakarta.inject.Singleton

@Singleton
class PropertiesBookService(
    @Named(PropertiesMapper.NAME) private val propertiesMapper: ObjectMapper
) {
    fun propertiesMapper(): ObjectMapper = propertiesMapper
}
