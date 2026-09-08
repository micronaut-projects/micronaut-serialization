package example;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record CsvPointRow(String x, String y, String visible) {
}
