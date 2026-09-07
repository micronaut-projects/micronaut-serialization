package io.micronaut.serde.protobuf;

import io.micronaut.serde.exceptions.SerdeException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Field numbers are the compatibility contract of a protobuf message, so a bad numbering has to be
 * refused rather than silently encoded.
 */
class ProtoSchemaValidationTest {

    @Test
    void rejectsDuplicateFieldNumbers() {
        SerdeException e = assertThrows(SerdeException.class,
            () -> ProtoSchema.of(InvalidModels.DuplicateNumbers.class));
        assertTrue(e.getMessage().contains("both declare field number 1"), e.getMessage());
    }

    @Test
    void rejectsADerivedNumberThatAnotherPropertyAlreadyClaims() {
        // 'first' would take 1 from its position, but 'second' declares 1 explicitly
        SerdeException e = assertThrows(SerdeException.class,
            () -> ProtoSchema.of(InvalidModels.DerivedClashesWithExplicit.class));
        assertTrue(e.getMessage().contains("[first]"), e.getMessage());
        assertTrue(e.getMessage().contains("takes field number 1 from its position"), e.getMessage());
        assertTrue(e.getMessage().contains("[second]"), e.getMessage());
    }

    @Test
    void rejectsNumbersProtobufReserves() {
        SerdeException e = assertThrows(SerdeException.class,
            () -> ProtoSchema.of(InvalidModels.ReservedNumber.class));
        assertTrue(e.getMessage().contains("reserved by Protocol Buffers"), e.getMessage());
    }

    @Test
    void rejectsNumbersOutsideTheLegalRange() {
        SerdeException e = assertThrows(SerdeException.class,
            () -> ProtoSchema.of(InvalidModels.NumberOutOfRange.class));
        assertTrue(e.getMessage().contains("outside the legal range"), e.getMessage());
    }

    @Test
    void rejectsTypesWithoutAnIntrospection() {
        SerdeException e = assertThrows(SerdeException.class, () -> ProtoSchema.of(Thread.class));
        assertTrue(e.getMessage().contains("No introspection found"), e.getMessage());
    }

    @Test
    void rejectsAProtoTypeThatDoesNotMatchTheJavaType() {
        SerdeException stringError = assertThrows(SerdeException.class,
            () -> ProtoSchema.of(InvalidModels.StringWithNumericType.class));
        assertTrue(stringError.getMessage().contains("incompatible with Java type [java.lang.String]"), stringError.getMessage());

        SerdeException narrowingError = assertThrows(SerdeException.class,
            () -> ProtoSchema.of(InvalidModels.LongWithNarrowType.class));
        assertTrue(narrowingError.getMessage().contains("incompatible with Java type [long]"), narrowingError.getMessage());
    }
}
