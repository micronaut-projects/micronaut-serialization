package example;

import io.micronaut.core.type.Argument;
import io.micronaut.serde.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

final class CsvRowsExample {

    List<List<String>> readRows(ObjectMapper csvMapper) throws IOException {
        String csv = "1,2,true\n" +
            "2,9,false\n" +
            "-13,0,true\n";
        Argument<List<List<String>>> target = Argument.listOf(
            Argument.listOf(String.class)
        );
        return csvMapper.readValue(csv.getBytes(StandardCharsets.UTF_8), target);
    }
}
