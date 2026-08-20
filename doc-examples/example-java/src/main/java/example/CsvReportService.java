package example;

import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.csv.CsvMapper;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
final class CsvReportService {
    private final ObjectMapper csvMapper;

    CsvReportService(@Named(CsvMapper.NAME) ObjectMapper csvMapper) {
        this.csvMapper = csvMapper;
    }

    ObjectMapper csvMapper() {
        return csvMapper;
    }
}
