package example

import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.yaml.YamlObjectMapper
import jakarta.inject.Named
import jakarta.inject.Singleton

@Singleton
class YamlReportService(
    @Named(YamlObjectMapper.YAML) private val yamlMapper: ObjectMapper
) {
    fun yamlMapper(): ObjectMapper = yamlMapper
}
