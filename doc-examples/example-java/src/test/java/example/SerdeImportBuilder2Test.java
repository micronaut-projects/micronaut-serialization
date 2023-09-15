package example;

import io.micronaut.serde.ObjectMapper;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

@MicronautTest
class SerdeImportBuilder2Test {

    @Inject ObjectMapper mapper;

    @Test
    void testImportedSerializers() throws IOException {
        BuilderBean bean = BuilderBean.builder().foo("bar").build();
        String json = mapper.writeValueAsString(bean);
        Assertions.assertEquals("{\"foo\":\"bar\"}", json);
        Assertions.assertEquals(bean, mapper.readValue(json, BuilderBean.class));
    }
}
