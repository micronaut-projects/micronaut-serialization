package io.micronaut.serde.support;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.ObjectMapper;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.exceptions.SerdeException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class MissingCustomDeserializerRetryTest {

    @Test
    void repeatedAttemptsShouldNotCauseNpeAfterInitializationFailure() {
        try (ApplicationContext ctx = ApplicationContext.run()) {
            ObjectMapper objectMapper = ctx.getBean(ObjectMapper.class);

            SerdeException first = Assertions.assertThrows(SerdeException.class,
                () -> objectMapper.readValue("{\"value\":\"test\"}", BeanWithMissingDeserializer.class));
            Assertions.assertTrue(first.getMessage().contains("Cannot find deserializer"));
            Assertions.assertFalse(containsNullPointerException(first));

            SerdeException second = Assertions.assertThrows(SerdeException.class,
                () -> objectMapper.readValue("{\"value\":\"test\"}", BeanWithMissingDeserializer.class));
            Assertions.assertTrue(second.getMessage().contains("Cannot find deserializer"));
            Assertions.assertFalse(containsNullPointerException(second));
        }
    }

    private static boolean containsNullPointerException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof NullPointerException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @Serdeable
    @Introspected
    static class BeanWithMissingDeserializer {
        @Serdeable.Deserializable(using = MissingStringDeserializer.class)
        private String value;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }

    abstract static class MissingStringDeserializer implements io.micronaut.serde.Deserializer<String> {
    }
}
