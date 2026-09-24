package example;

import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.toon.ToonMapper;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
final class ToonReportService {
    private final ObjectMapper toonMapper;

    ToonReportService(@Named(ToonMapper.NAME) ObjectMapper toonMapper) {
        this.toonMapper = toonMapper;
    }

    ObjectMapper toonMapper() {
        return toonMapper;
    }
}
