package example;

import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.properties.PropertiesMapper;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
public class PropertiesBookService {
    private final ObjectMapper propertiesMapper;

    public PropertiesBookService(@Named(PropertiesMapper.NAME) ObjectMapper propertiesMapper) {
        this.propertiesMapper = propertiesMapper;
    }

    public ObjectMapper propertiesMapper() {
        return propertiesMapper;
    }
}
