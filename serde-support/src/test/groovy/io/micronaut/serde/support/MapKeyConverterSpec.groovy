package io.micronaut.serde.support

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.convert.ConversionContext
import io.micronaut.core.convert.TypeConverter
import io.micronaut.core.type.Argument
import io.micronaut.serde.ObjectMapper
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class MapKeyConverterSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run(['spec.name': 'MapKeyConverterSpec'])

    void "a registered converter still decides how map keys are read"() {
        given:
        def mapper = context.getBean(ObjectMapper)

        when:
        Map<Long, String> read = mapper.readValue('{"1":"a","22":"b"}', Argument.mapOf(Long, String))

        then:
        read == [(1001L): "a", (1022L): "b"]
    }

    @Singleton
    @Requires(property = 'spec.name', value = 'MapKeyConverterSpec')
    static class OffsetLongConverter implements TypeConverter<String, Long> {
        @Override
        Optional<Long> convert(String object, Class<Long> targetType, ConversionContext context) {
            return Optional.of(Long.parseLong(object) + 1000L)
        }
    }
}
