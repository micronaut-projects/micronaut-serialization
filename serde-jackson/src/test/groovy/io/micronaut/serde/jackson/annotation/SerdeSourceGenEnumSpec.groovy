package io.micronaut.serde.jackson.annotation

import io.micronaut.core.type.Argument
import io.micronaut.serde.Deserializer
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.Serializer
import io.micronaut.serde.jackson.JsonCompileSpec

class SerdeSourceGenEnumSpec extends JsonCompileSpec {

    void 'test enum sourcegen serializer and deserializer are concrete and functional'() {
        given:
        def context = buildContext('test.TestEnum', '''
package test;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Introspected;

@Serdeable
@Introspected
public enum TestEnum {
    A,
    B
}
''')
        Class<?> enumType = context.classLoader.loadClass('test.TestEnum')
        def registry = context.getBean(SerdeRegistry)
        def type = Argument.of(enumType)

        expect:
        assertGeneratedSerializer(registry, type)
        assertGeneratedDeserializer(registry, type)

        when:
        def value = Enum.valueOf((Class<Enum>) enumType, 'B')
        String json = jsonMapper.writeValueAsString(value)
        def deserialized = jsonMapper.readValue(json, type)

        then:
        json == '"B"'
        deserialized == value

        cleanup:
        context.close()
    }

    void 'test enum generated serializer writes pre-encoded names with any encoder'() {
        given:
        def context = buildContext('test.UnicodeEnum', '''
package test;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public enum UnicodeEnum {
    FIRST,
    \u00c9T\u00c9,
    \u65e5\u672c
}
''')
        Class<?> enumType = context.classLoader.loadClass('test.UnicodeEnum')
        def registry = context.getBean(SerdeRegistry)
        def type = Argument.of(enumType)
        def values = enumType.enumConstants as List

        expect:
        assertGeneratedSerializer(registry, type)
        values.collect { jsonMapper.writeValueAsString(it) } == ['"FIRST"', '"\u00c9T\u00c9"', '"\u65e5\u672c"']
        values.collect { jsonMapper.writeValueToTree(it).getValue() } == ['FIRST', '\u00c9T\u00c9', '\u65e5\u672c']
        jsonMapper.writeValueAsString(values) == '["FIRST","\u00c9T\u00c9","\u65e5\u672c"]'
        values.collect { jsonMapper.readValue(jsonMapper.writeValueAsString(it), type) } == values

        cleanup:
        context.close()
    }

    void 'test enum overridden wire names with escaped characters'() {
        given:
        def context = buildContext('test.WireEnum', """
package test;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public enum WireEnum {
    FIRST,
    @JsonProperty("second-wire")
    SECOND,
    @JsonProperty("\\u00e9t\\u00e9 \\"q\\"")
    THIRD
}
""")
        Class<?> enumType = context.classLoader.loadClass('test.WireEnum')
        def type = Argument.of(enumType)
        def values = enumType.enumConstants as List

        expect:
        values.collect { jsonMapper.writeValueAsString(it) } == ['"FIRST"', '"second-wire"', '"\u00e9t\u00e9 \\"q\\""']
        values.collect { jsonMapper.writeValueToTree(it).getValue() } == ['FIRST', 'second-wire', '\u00e9t\u00e9 "q"']
        jsonMapper.writeValueAsString(values) == '["FIRST","second-wire","\u00e9t\u00e9 \\"q\\""]'
        values.collect { jsonMapper.readValue(jsonMapper.writeValueAsString(it), type) } == values

        cleanup:
        context.close()
    }

    void 'test enum generated serializer keeps duplicate wire names'() {
        given:
        def context = buildContext('test.DuplicateWireEnum', """
package test;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public enum DuplicateWireEnum {
    @JsonProperty("same")
    ONE,
    @JsonProperty("same")
    TWO
}
""")
        Class<?> enumType = context.classLoader.loadClass('test.DuplicateWireEnum')

        expect:
        (enumType.enumConstants as List).collect { jsonMapper.writeValueAsString(it) } == ['"same"', '"same"']

        cleanup:
        context.close()
    }

    void 'test enum generated deserializer is selected and functional'() {
        given:
        def context = buildContext('test.ParityEnum', '''
package test;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Introspected;

@Serdeable
@Introspected
public enum ParityEnum {
    A,
    B
}
''')
        Class<?> enumType = context.classLoader.loadClass('test.ParityEnum')
        def registry = context.getBean(SerdeRegistry)
        Deserializer.DecoderContext decoderContext = registry.newDecoderContext(Object)
        def type = Argument.of(enumType)
        Deserializer defaultDeserializer = registry.findDeserializer(type)
        Deserializer specificDeserializer = defaultDeserializer.createSpecific(decoderContext, type)

        expect:
        specificDeserializer.class.name == generatedClassName(enumType, 'Deserializer')

        when:
        def deserialized = jsonMapper.readValue('"B"', type)

        then:
        deserialized.toString() == 'B'

        cleanup:
        context.close()
    }

    void 'test repeated serde import does not duplicate enum sourcegen files'() {
        given:
        def context = buildContext('test.ImportHolder', '''
package test;

import io.micronaut.serde.annotation.SerdeImport;

@SerdeImport(value = ImportHolder.McpSchema.Role.class)
@SerdeImport(value = ImportHolder.McpSchema.Role.class)
public class ImportHolder {
    public static final class McpSchema {
        public enum Role {
            USER,
            ASSISTANT
        }
    }
}
''')
        Class<?> enumType = context.classLoader.loadClass('test.ImportHolder$McpSchema$Role')

        when:
        def value = Enum.valueOf((Class<Enum>) enumType, 'ASSISTANT')
        String json = jsonMapper.writeValueAsString(value)
        def deserialized = jsonMapper.readValue(json, Argument.of(enumType))

        then:
        json == '"ASSISTANT"'
        deserialized == value

        cleanup:
        context.close()
    }

    private static void assertGeneratedSerializer(SerdeRegistry registry, Argument argument) {
        Serializer serializer = registry.findSerializer(argument).createSpecific(registry.newEncoderContext(Object), argument)
        assert serializer.class.name == generatedClassName(argument.type, 'Serializer')
    }

    private static void assertGeneratedDeserializer(SerdeRegistry registry, Argument argument) {
        Deserializer deserializer = registry.findDeserializer(argument).createSpecific(registry.newDecoderContext(Object), argument)
        assert deserializer.class.name == generatedClassName(argument.type, 'Deserializer')
    }

    private static String generatedClassName(Class<?> type, String suffix) {
        String packageName = type.package.name
        String localName = type.name
        if (packageName) {
            localName = localName.substring(packageName.length() + 1)
        }
        "${packageName ? packageName + '.' : ''}Serde${localName.replace('.', '_').replace('$', '_')}${suffix}"
    }
}
