package example;

import io.micronaut.serde.csv.error.CsvException;
import io.micronaut.serde.csv.mapper.CsvMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
class PersonTest {

    @Test
    void writesAndReadsCsv(CsvMapper csvMapper) throws CsvException {
        List<Person> people = List.of(new Person("Alice", 30), new Person("Bob", 25));

        String csv = csvMapper.writeToString(people);
        assertEquals("name,age\nAlice,30\nBob,25\n", csv);

        List<Person> decoded = new ArrayList<>();
        csvMapper.readAll(csv, Person.class).forEach(decoded::add);
        assertEquals(people, decoded);
    }
}
