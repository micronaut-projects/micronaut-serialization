package io.micronaut.serde.jackson.object

import io.micronaut.core.type.Argument
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.exceptions.SerdeException
import io.micronaut.serde.jackson.JsonCompileSpec
import spock.lang.Unroll

/**
 * The runtime serializer of plain beans checks the NON_NULL, NON_ABSENT and NON_EMPTY inclusions
 * itself, and writes the same as the general serializer.
 */
class RuntimeInclusionSpec extends JsonCompileSpec {

    private static final String SOURCE = '''
package test;

import io.micronaut.serde.annotation.Serdeable;
import java.util.List;
import java.util.Optional;

@Serdeable
public class Bean {
    private String text;
    private int number;
    private List<String> list;
    private Optional<String> optional;
    private Nested nested;

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public int getNumber() { return number; }
    public void setNumber(int number) { this.number = number; }
    public List<String> getList() { return list; }
    public void setList(List<String> list) { this.list = list; }
    public Optional<String> getOptional() { return optional; }
    public void setOptional(Optional<String> optional) { this.optional = optional; }
    public Nested getNested() { return nested; }
    public void setNested(Nested nested) { this.nested = nested; }
}

@Serdeable
class Nested {
    private String name;
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}

@Serdeable
class Failing {
    public String getValue() { throw new IllegalStateException("broken getter"); }
}

@Serdeable
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
class ClassNonNull {
    private String text;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
    private String always;
    private List<String> list;

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getAlways() { return always; }
    public void setAlways(String always) { this.always = always; }
    public List<String> getList() { return list; }
    public void setList(List<String> list) { this.list = list; }
}

@Serdeable
class WithNonDefault {
    private String text;
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_DEFAULT)
    private int count;

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }
}

@Serdeable
class FailingCount {
    public int getCount() { throw new IllegalStateException("broken getter"); }
}
'''

    @Unroll
    void "the runtime serializer writes #inclusion like the general serializer"() {
        given:
        def context = buildContext('test.Bean', SOURCE, true, [
            'micronaut.serde.serialization.inclusion': inclusion,
            'micronaut.serde.serialization.disable-generated-serializer': 'true'
        ])
        def type = argumentOf(context, 'test.Bean')
        def empty = newInstance(context, 'test.Bean', [text: '', number: 0, list: [], optional: Optional.empty(), nested: null])
        def filled = newInstance(context, 'test.Bean', [text: 'a', number: 1, list: ['x'], optional: Optional.of('o'), nested: newInstance(context, 'test.Nested', [name: 'n'])])

        expect:
        serializerOf(context, type).class.simpleName == 'SimpleObjectSerializer'
        jsonMapper.writeValueAsString(type, empty) == emptyJson
        jsonMapper.writeValueAsString(type, filled) == '{"text":"a","number":1,"list":["x"],"optional":"o","nested":{"name":"n"}}'
        jsonMapper.writeValueAsString(type, newInstance(context, 'test.Bean', [:])) == unsetJson

        cleanup:
        context.close()

        where:
        inclusion    | emptyJson                                                         | unsetJson
        'ALWAYS'     | '{"text":"","number":0,"list":[],"optional":null,"nested":null}' | '{"text":null,"number":0,"list":null,"optional":null,"nested":null}'
        'NON_NULL'   | '{"text":"","number":0,"list":[],"optional":null}'               | '{"number":0}'
        'NON_ABSENT' | '{"text":"","number":0,"list":[]}'                               | '{"number":0}'
        'NON_EMPTY'  | '{"number":0}'                                                    | '{"number":0}'
    }

    void "a failing getter is reported with the property, as by the general serializer"() {
        given:
        def context = buildContext('test.Bean', SOURCE, true, [
            'micronaut.serde.serialization.disable-generated-serializer': 'true'
        ])
        def type = argumentOf(context, 'test.Failing')

        when:
        jsonMapper.writeValueAsString(type, newInstance(context, 'test.Failing', [:]))

        then:
        def e = thrown(SerdeException)
        e.message.contains('Error getting property')
        e.message.contains('broken getter')

        cleanup:
        context.close()
    }

    void "a failing primitive getter leaves no key without a value in the output stream"() {
        given:
        def context = buildContext('test.Bean', SOURCE, true, [
            'micronaut.serde.serialization.disable-generated-serializer': 'true'
        ])
        def type = argumentOf(context, 'test.FailingCount')
        def out = new ByteArrayOutputStream()

        when:
        jsonMapper.writeValue(out, type, newInstance(context, 'test.FailingCount', [:]))

        then:
        def e = thrown(SerdeException)
        e.message.contains('broken getter')
        !out.toString().contains('count')

        cleanup:
        context.close()
    }

    @Unroll
    void "the inclusions of #name annotations are written like the generated serializer, by the #serializerName"() {
        given:
        def generatedContext = buildContext('test.Bean', SOURCE, true, [:])
        def generatedMapper = jsonMapper
        def context = buildContext('test.Bean', SOURCE, true, [
            'micronaut.serde.serialization.disable-generated-serializer': 'true'
        ])
        def type = argumentOf(context, beanName)
        def values = valuesOf.collect { newInstance(context, beanName, it) }
        def generatedValues = valuesOf.collect { newInstance(generatedContext, beanName, it) }

        expect:
        serializerOf(context, type).class.simpleName == serializerName
        values.collect { jsonMapper.writeValueAsString(type, it) } == expected
        generatedValues.collect { generatedMapper.writeValueAsString(argumentOf(generatedContext, beanName), it) } == expected

        cleanup:
        context.close()
        generatedContext.close()

        where:
        name                                    | beanName            | serializerName               | valuesOf                                              | expected
        'class NON_NULL and property ALWAYS'    | 'test.ClassNonNull' | 'SimpleObjectSerializer'     | [[:], [text: 'a', always: 'b', list: []]]             | ['{"always":null}', '{"text":"a","always":"b","list":[]}']
        'a property NON_DEFAULT'                | 'test.WithNonDefault' | 'CustomizedObjectSerializer' | [[:], [text: 'a', count: 2]]                        | ['{}', '{"text":"a","count":2}']
    }

    private static Object serializerOf(context, Argument<?> type) {
        def registry = context.getBean(SerdeRegistry)
        try (def encoderContext = registry.newEncoderContext(null)) {
            def serializer = encoderContext.findSerializer(type).createSpecific(encoderContext, type)
            return serializer.@serializer
        }
    }
}
