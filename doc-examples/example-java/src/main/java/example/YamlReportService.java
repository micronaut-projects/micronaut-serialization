package example;

import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.yaml.YamlObjectMapper;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
final class YamlReportService {
    private final ObjectMapper yamlMapper;

    YamlReportService(@Named(YamlObjectMapper.YAML) ObjectMapper yamlMapper) {
        this.yamlMapper = yamlMapper;
    }

    ObjectMapper yamlMapper() {
        return yamlMapper;
    }
}
