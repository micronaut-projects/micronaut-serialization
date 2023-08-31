package example;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import com.oracle.bmc.aidocument.model.AnalyzeDocumentResult;
import com.oracle.bmc.aidocument.model.DocumentClassificationFeature;
import com.oracle.bmc.aidocument.model.DocumentKeyValueExtractionFeature;
import com.oracle.bmc.aidocument.model.DocumentMetadata;
import com.oracle.bmc.aidocument.model.Page;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.PropertyFilter;
import io.micronaut.serde.Serializer;
import io.micronaut.test.annotation.MockBean;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@MicronautTest
class SerdeImportBuilderTest {

    @Inject ObjectMapper mapper;

    @Test
    void testImportedSerializers() throws IOException {
        AnalyzeDocumentResult analyzeDocumentDetails =
            AnalyzeDocumentResult.builder()
                .documentClassificationModelVersion("1.0")
                .documentMetadata(DocumentMetadata.builder().pageCount(300).build())
                .pages(List.of(Page.builder().pageNumber(10).build()))
                .build();
        String result = mapper.writeValueAsString(analyzeDocumentDetails);
        Assertions.assertNotNull(result);

        AnalyzeDocumentResult read = mapper.readValue(result, AnalyzeDocumentResult.class);
        Assertions.assertEquals(analyzeDocumentDetails, read);
        Assertions.assertEquals(analyzeDocumentDetails.getDocumentClassificationModelVersion(), read.getDocumentClassificationModelVersion());

    }

    @Singleton
    @Named("explicitlySetFilter")
    static class TestPropertyFilter implements PropertyFilter {

        @Override
        public boolean shouldInclude(Serializer.EncoderContext encoderContext, Serializer<Object> propertySerializer, Object bean, String propertyName, Object propertyValue) {
            return true;
        }
    }
}
