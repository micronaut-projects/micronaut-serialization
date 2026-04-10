package example;

import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.annotation.SerdeImport;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
@SerdeImport(ImportedPojo.class)
@SerdeImport(ImportedMappedEntity.class)
class MappedEntitySerdeImportTest {

    @Test
    void testSerdeImportOnTestClassSupportsPlainPojo(ObjectMapper objectMapper) throws IOException {
        String json = objectMapper.writeValueAsString(new ImportedPojo(1L, "plain"));
        assertEquals(
            "{\"id\":1,\"name\":\"plain\"}",
            json
        );
        ImportedPojo read = objectMapper.readValue(json, ImportedPojo.class);
        assertEquals(1L, read.getId());
        assertEquals("plain", read.getName());
    }

    @Test
    void testSerdeImportOnTestClassSupportsMappedEntity(ObjectMapper objectMapper) throws IOException {
        String json = objectMapper.writeValueAsString(new ImportedMappedEntity(1L, "mapped"));
        assertEquals(
            "{\"id\":1,\"name\":\"mapped\"}",
            json
        );
        ImportedMappedEntity read = objectMapper.readValue(json, ImportedMappedEntity.class);
        assertEquals(1L, read.getId());
        assertEquals("mapped", read.getName());
    }
}
