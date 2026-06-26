package example;

import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.properties.PropertiesMapper;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
final class PropertiesBookService {
    private final ObjectMapper propertiesMapper;

    PropertiesBookService(@Named(PropertiesMapper.NAME) ObjectMapper propertiesMapper) {
        this.propertiesMapper = propertiesMapper;
    }

    ObjectMapper propertiesMapper() {
        return propertiesMapper;
    }
}
