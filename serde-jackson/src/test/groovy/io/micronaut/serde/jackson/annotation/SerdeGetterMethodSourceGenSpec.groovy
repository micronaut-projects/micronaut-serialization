package io.micronaut.serde.jackson.annotation

import io.micronaut.context.ApplicationContext
import io.micronaut.core.type.Argument
import io.micronaut.json.JsonMapper
import io.micronaut.serde.SerdeRegistry
import io.micronaut.serde.jackson.JsonCompileSpec

/**
 * Methods that declare a property through {@code @JsonProperty} or {@code @JsonGetter} without being
 * property accessors are written by the generated serializers as the runtime serializer writes them, and
 * the methods the generated serializers cannot call leave the type to the runtime serializer.
 */
class SerdeGetterMethodSourceGenSpec extends JsonCompileSpec {

    private static final Map<String, Object> RUNTIME_SERDES = [
        'micronaut.serde.serialization.disable-generated-serializer'    : true,
        'micronaut.serde.deserialization.disable-generated-deserializer': true
    ]

    void 'test record methods declaring a property are written by the generated serializer'() {
        given:
        String source = '''
package test;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;

import java.util.List;

@Serdeable
public record GetterRecord(String name, int count) {

    @JsonProperty("computed")
    public String computed() {
        return name + "!";
    }

    @JsonProperty
    public int doubled() {
        return count * 2;
    }

    @JsonGetter("tags")
    public List<String> tags() {
        return List.of(name, String.valueOf(count));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("missing")
    public String missing() {
        return null;
    }

    @JsonProperty("dollar")
    public String dollar$() {
        return "$" + count;
    }

    @JacksonXmlProperty(localName = "ref", isAttribute = true, namespace = "urn:test")
    public String reference() {
        return "#" + name;
    }
}
'''

        when:
        def generated = serialize('test.GetterRecord', source, [:]) { Class<?> type -> type.getDeclaredConstructor(String, int).newInstance('x', 21) }
        def runtime = serialize('test.GetterRecord', source, RUNTIME_SERDES) { Class<?> type -> type.getDeclaredConstructor(String, int).newInstance('x', 21) }

        then:
        generated.serializer == 'test.SerdeGetterRecordSerializer'
        runtime.serializer != 'test.SerdeGetterRecordSerializer'
        // An XML attribute property is written first, as the runtime serializer orders it
        generated.json == '{"ref":"#x","name":"x","count":21,"computed":"x!","doubled":42,"tags":["x","21"],"dollar":"$21"}'
        generated.json == runtime.json
    }

    void 'test bean methods declaring a property are written by the generated serializer'() {
        given:
        String source = '''
package test;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
@JsonPropertyOrder({"letters", "name"})
public class GetterBean {

    private String name;

    @JsonGetter("name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @JsonProperty("display")
    public String display() {
        return "<" + name + ">";
    }

    @JsonGetter("letters")
    public List<String> letters() {
        return List.of(name.split(""));
    }

    @JsonIgnore
    @JsonProperty("hidden")
    public String hidden() {
        return "never written";
    }
}
'''

        when:
        def generated = serialize('test.GetterBean', source, [:]) { Class<?> type -> type.getDeclaredConstructor().newInstance().tap { name = 'ab' } }
        def runtime = serialize('test.GetterBean', source, RUNTIME_SERDES) { Class<?> type -> type.getDeclaredConstructor().newInstance().tap { name = 'ab' } }

        then:
        generated.serializer == 'test.SerdeGetterBeanSerializer'
        runtime.serializer != 'test.SerdeGetterBeanSerializer'
        generated.json == '{"letters":["a","b"],"name":"ab","display":"<ab>"}'
        generated.json == runtime.json
    }

    void 'test a method the generated serializer cannot write leaves the type to the runtime serializer'() {
        given:
        String source = '''
package test;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
public record IterableGetterRecord(String name) {

    @JsonProperty("items")
    public Iterable<String> items() {
        return List.of(name, name);
    }
}
'''

        when:
        def result = serialize('test.IterableGetterRecord', source, [:]) { Class<?> type -> type.getDeclaredConstructor(String).newInstance('x') }

        then:
        result.serializer != 'test.SerdeIterableGetterRecordSerializer'
        result.deserializer == 'test.SerdeIterableGetterRecordDeserializer'
        result.json == '{"name":"x","items":["x","x"]}'
    }

    void 'test an inherited method with an unsupported annotation leaves the type to the runtime serializer'() {
        given:
        String source = '''
package test;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
import io.micronaut.serde.annotation.Serdeable;

class GetterBase {

    @JsonRawValue
    @JsonProperty("raw")
    public String raw() {
        return "[1]";
    }
}

@Serdeable
public class InheritedGetterBean extends GetterBase {

    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
'''

        when:
        def generated = serialize('test.InheritedGetterBean', source, [:]) { Class<?> type -> type.getDeclaredConstructor().newInstance().tap { name = 'ab' } }
        def runtime = serialize('test.InheritedGetterBean', source, RUNTIME_SERDES) { Class<?> type -> type.getDeclaredConstructor().newInstance().tap { name = 'ab' } }

        then:
        generated.serializer != 'test.SerdeInheritedGetterBeanSerializer'
        generated.json == runtime.json
    }

    private Map<String, String> serialize(String className, String source, Map<String, Object> contextProperties, Closure<Object> instance) {
        ApplicationContext context = buildContext(className, source, true, contextProperties)
        try {
            Class<?> type = context.classLoader.loadClass(className)
            Argument<?> argument = Argument.of(type)
            def registry = context.getBean(SerdeRegistry)
            return [
                serializer  : registry.findSerializer(argument).createSpecific(registry.newEncoderContext(Object), argument).class.name,
                deserializer: registry.findDeserializer(argument).createSpecific(registry.newDecoderContext(Object), argument).class.name,
                json        : serializeToString(context.getBean(JsonMapper), instance.call(type))
            ]
        } finally {
            context.close()
        }
    }
}
