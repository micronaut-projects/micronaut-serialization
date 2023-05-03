package io.micronaut.serde.support.serdes

import io.micronaut.context.ApplicationContext
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.config.SerdeConfiguration.NumericTimeUnit
import io.micronaut.serde.config.SerdeConfiguration.TimeShape
import spock.lang.Specification

import java.time.Instant

class InstantSerdeSpec extends Specification {
    def 'serialization shapes'(
            TimeShape shape,
            NumericTimeUnit unit,
            String expected
    ) {
        given:
        def ctx = ApplicationContext.run([
                'micronaut.serde.time-write-shape' : shape,
                'micronaut.serde.numeric-time-unit': unit,
        ])
        when:
        String json = ctx.getBean(ObjectMapper).writeValueAsString(Instant.ofEpochSecond(123, 456789123))

        then:
        json == expected

        cleanup:
        ctx.close()

        where:
        shape             | unit                         | expected
        TimeShape.STRING  | NumericTimeUnit.SECONDS      | "\"1970-01-01T00:02:03.456789123Z\""
        TimeShape.INTEGER | NumericTimeUnit.SECONDS      | "123"
        TimeShape.INTEGER | NumericTimeUnit.LEGACY       | "123456"
        TimeShape.INTEGER | NumericTimeUnit.MILLISECONDS | "123456"
        TimeShape.INTEGER | NumericTimeUnit.NANOSECONDS  | "123456789123"
        TimeShape.DECIMAL | NumericTimeUnit.SECONDS      | "123.456789123"
        TimeShape.DECIMAL | NumericTimeUnit.LEGACY       | "123456.789123"
        TimeShape.DECIMAL | NumericTimeUnit.MILLISECONDS | "123456.789123"
        TimeShape.DECIMAL | NumericTimeUnit.NANOSECONDS  | "123456789123"
    }

    def 'deserialization shapes'(
            NumericTimeUnit unit,
            String input
    ) {
        given:
        def ctx = ApplicationContext.run([
                'micronaut.serde.numeric-time-unit': unit,
        ])
        when:
        Instant parsed = ctx.getBean(ObjectMapper).readValue(input, Instant)

        then:
        parsed == Instant.ofEpochSecond(123, 456789123)

        cleanup:
        ctx.close()

        where:
        unit                         | input
        NumericTimeUnit.SECONDS      | "\"1970-01-01T00:02:03.456789123Z\""
        NumericTimeUnit.MILLISECONDS | "\"1970-01-01T00:02:03.456789123Z\""
        NumericTimeUnit.NANOSECONDS  | "\"1970-01-01T00:02:03.456789123Z\""
        NumericTimeUnit.SECONDS      | "123.456789123"
        NumericTimeUnit.MILLISECONDS | "123456.789123"
        NumericTimeUnit.NANOSECONDS  | "123456789123"
    }
}
