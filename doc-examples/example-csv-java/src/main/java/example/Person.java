package example;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.csv.annotation.CsvFormat;

@Serdeable // <1>
@CsvFormat(header = CsvFormat.Header.PRESENT) // <2>
public record Person(String name, int age) {
}
