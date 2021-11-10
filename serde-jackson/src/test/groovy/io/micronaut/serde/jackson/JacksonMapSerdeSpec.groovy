package io.micronaut.serde.jackson


import io.micronaut.http.HttpStatus
import io.micronaut.serde.AbstractJsonCompileSpec
import io.micronaut.serde.jackson.maps.CustomKey

class JacksonMapSerdeSpec extends AbstractJsonCompileSpec implements io.micronaut.serde.JsonSpec {

    void "test serialize / deserialize maps with enum keys"() {
        given:
        def context = buildContext('''
package test;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.http.HttpStatus;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
@JsonInclude(JsonInclude.Include.NON_EMPTY)
class EnumKeys {

    private final Map<HttpStatus, Integer> statusCodes;

    public EnumKeys(Map<HttpStatus, Integer> statusCodes) {
        this.statusCodes = statusCodes;
    }

    public Map<HttpStatus, Integer> getStatusCodes() {
        return statusCodes;
    }
}

''')
        def bean = newInstance(context, 'test.EnumKeys', [:])
        def beanType = argumentOf(context, 'test.EnumKeys')

        when:"empty map is written"
        def result = writeJson(jsonMapper, bean)

        then:
        result == "{}"

        when:
        bean = newInstance(context, 'test.EnumKeys', [(HttpStatus.OK): 200])

        result = writeJson( jsonMapper, bean)
        then:
        result == '{"statusCodes":{"OK":200}}'

        when:
        bean = jsonMapper.readValue(result, beanType)

        then:
        bean.statusCodes == [(HttpStatus.OK): 200]

    }

    void "test serialize / deserialize maps with custom keys"() {
        given:
        def context = buildContext('''
package test;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.TypeConverter;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.jackson.maps.CustomKey;
import jakarta.inject.Singleton;
import java.util.Optional;

@Serdeable
@JsonInclude(JsonInclude.Include.NON_EMPTY)
class CustomKeys {
    private final Map<CustomKey, Integer> data;

    public CustomKeys(Map<CustomKey, Integer> data) {
        this.data = data;
    }

    public Map<CustomKey, Integer> getData() {
        return data;
    }
}

@Singleton
class CustomKeyConverter implements TypeConverter<CustomKey, String> {
    @Override 
    public Optional<String> convert(CustomKey object, Class<String> targetType, ConversionContext context) {
        return Optional.of(object.getName());
    }
}

@Singleton
class CustomKeyConverter2 implements TypeConverter<String, CustomKey> {
    @Override 
    public Optional<CustomKey> convert(String object, Class<CustomKey> targetType, ConversionContext context) {
        return Optional.of(new CustomKey(object));
    }    
}

''')
        def bean = newInstance(context, 'test.CustomKeys', [:])
        def beanType = argumentOf(context, 'test.CustomKeys')

        when:"empty map is written"
        def result = writeJson(jsonMapper, bean)

        then:
        result == "{}"

        when:
        bean = newInstance(context, 'test.CustomKeys', [(new CustomKey("foo")): 200])

        result = writeJson( jsonMapper, bean)
        then:
        result == '{"data":{"foo":200}}'

        when:
        bean = jsonMapper.readValue(result, beanType)

        then:
        bean.data == [(new CustomKey("foo")): 200]

    }
}
