package example;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.csv.CsvMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
class CsvQuickStartTest {

    @Test
    void testReadRows(@Named(CsvMapper.NAME) ObjectMapper csvMapper) throws IOException {
        String csv = "1,2,true\n" +
            "2,9,false\n" +
            "-13,0,true\n";
        Argument<List<List<String>>> target = Argument.listOf(
            Argument.listOf(String.class)
        );

        List<List<String>> rows = csvMapper.readValue(csv.getBytes(StandardCharsets.UTF_8), target);

        assertEquals(List.of("1", "2", "true"), rows.get(0));
    }

    @Test
    void testReadBeanRowsWithHeader() throws IOException {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "micronaut.serde.format.csv.read-features.header", "FIRST_ROW"
        ))) {
            ObjectMapper csvMapper = context.getBean(CsvMapper.class);
            String csv = "x,y,visible\n" +
                "1,2,true\n" +
                "2,9,false\n" +
                "-13,0,true\n";

            List<CsvPointRow> rows = csvMapper.readValue(csv.getBytes(StandardCharsets.UTF_8), Argument.listOf(CsvPointRow.class));

            assertEquals(new CsvPointRow("1", "2", "true"), rows.get(0));
        }
    }

    @Test
    void testReadMapRowsWithHeader() throws IOException {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "micronaut.serde.format.csv.read-features.header", "FIRST_ROW"
        ))) {
            ObjectMapper csvMapper = context.getBean(CsvMapper.class);
            String csv = "x,y,visible\n" +
                "1,2,true\n" +
                "2,9,false\n" +
                "-13,0,true\n";
            Argument<List<Map<String, String>>> target = Argument.listOf(
                Argument.mapOf(String.class, String.class)
            );

            List<Map<String, String>> rows = csvMapper.readValue(csv.getBytes(StandardCharsets.UTF_8), target);

            assertEquals(Map.of("x", "1", "y", "2", "visible", "true"), rows.get(0));
        }
    }

    @Test
    void testWriteBeanRowsWithHeader() throws IOException {
        try (ApplicationContext context = ApplicationContext.run(Map.of(
            "micronaut.serde.format.csv.write-features.header", "FIRST_ROW"
        ))) {
            ObjectMapper csvMapper = context.getBean(CsvMapper.class);
            Argument<List<CsvPointRow>> target = Argument.listOf(CsvPointRow.class);
            List<CsvPointRow> rows = List.of(
                new CsvPointRow("1", "2", "true"),
                new CsvPointRow("2", "9", "false"),
                new CsvPointRow("-13", "0", "true")
            );

            String result = csvMapper.writeValueAsString(target, rows);

            assertEquals("x,y,visible\n1,2,true\n2,9,false\n-13,0,true\n", result);
        }
    }
}
