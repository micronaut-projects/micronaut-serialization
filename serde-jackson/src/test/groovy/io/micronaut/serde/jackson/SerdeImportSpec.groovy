package io.micronaut.serde.jackson

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import io.micronaut.core.beans.exceptions.IntrospectionException
import io.micronaut.core.type.Argument
import io.micronaut.health.HealthStatus
import io.micronaut.management.health.indicator.HealthResult
import one.microstream.storage.restadapter.types.ViewerObjectDescription
import spock.lang.Issue

class SerdeImportSpec extends JsonCompileSpec {

    void "test external mixin and external class"() {
        given:
        def context = buildContext('''
package externalmixin;

import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.lambda.runtime.serialization.events.mixins.SQSEventMixin;
import io.micronaut.serde.annotation.SerdeImport;

@SerdeImport(
    value = SQSEvent.class,
    mixin = SQSEventMixin.class
)
@SerdeImport(
    value = SQSEvent.SQSMessage.class,
    mixin = SQSEventMixin.SQSMessageMixin.class
)
class AddMixin {

}
''')
        def event = new SQSEvent()
        def message = new SQSEvent.SQSMessage(messageId:"test", eventSourceArn: "test-arn")
        event.records = [
                message
        ]

        when:
        def result = jsonMapper.writeValueAsString(event)

        then:
        result == '{"Records":[{"messageId":"test","eventSourceARN":"test-arn"}]}'

        cleanup:
        context.close()
    }

    void "test mixin constructor"() {
        def context = buildContext('mixintest.Test','''
package mixintest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.SerdeImport;
import io.micronaut.http.HttpStatus;
import io.micronaut.serde.annotation.Serdeable;

@SerdeImport(
    value = Test.class,
    mixin = TestMixin.class
)
class TestImport {}

interface HttpStatusInfo {
    String name();
    int code();
}

public class Test implements HttpStatusInfo {
    private HttpStatus status;
    Test(int code) {
        this.status = HttpStatus.valueOf(code);
    }
    @Override public String name() {
        return status.getReason();
    }
    @Override public int code() {
        return status.getCode();
    }
}

abstract class TestMixin  {
    private HttpStatus status;
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    TestMixin(int code) {
        this.status = HttpStatus.valueOf(code);
    }

    @JsonValue
    abstract int code();
}
''')
        def impl = argumentOf(context, 'mixintest.Test')
        def bean = impl.type.newInstance(200)

        expect:
        writeJson(jsonMapper, bean) == '200'

        def read = jsonMapper.readValue('200', typeUnderTest)
        read.name() == 'Ok'
        read.code() == 200

        cleanup:
        context.close()
    }

    void "test mixin constructor with parameter renamed"() {
        def context = buildContext('mixintest.Test','''
package mixintest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.SerdeImport;
import io.micronaut.http.HttpStatus;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;
@SerdeImport(
    value = Test.class,
    mixin = TestMixin.class
)
class TestImport {}

public class Test {
    private int code;
    Test(int code) {
        this.code = code;
    }
    public int getCode() {
        return code;
    }
}

abstract class TestMixin  {

    @JsonCreator
    TestMixin(@JsonProperty("customWrite") int code) {
    }

    @JsonProperty("customRead")
    abstract int getCode();

}

''')
        def impl = argumentOf(context, 'mixintest.Test')
        def bean = impl.type.newInstance(200)

        expect:
        writeJson(jsonMapper, bean) == '{"customRead":200}'

        def read = jsonMapper.readValue('{"customWrite":200}', typeUnderTest)
        read.getCode() == 200

        cleanup:
        context.close()
    }

    void "test import with interface"() {
        def context = buildContext('mixintest.HttpStatusInfo','''
package mixintest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.SerdeImport;
import io.micronaut.http.HttpStatus;
import io.micronaut.serde.annotation.Serdeable;

@SerdeImport(
    value = HttpStatusInfo.class,
    mixin = TestMixin.class
)
class TestImport {}

public interface HttpStatusInfo {
    String name();
    int code();
}

class Test implements HttpStatusInfo {
    private HttpStatus status;
    Test(int code) {
        this.status = HttpStatus.valueOf(code);
    }
    @Override public String name() {
        return status.getReason();
    }
    @Override public int code() {
        return status.getCode();
    }
}

// Skip validation for this mixin: the deserializable-as cannot be validated for a mixin
@Serdeable.Deserializable(validate = false, as = Another.class)
interface TestMixin {
    @JsonValue
    int code();
}

@Serdeable.Deserializable
class Another implements HttpStatusInfo {
    private HttpStatus status;
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    Another(int code) {
        this.status = HttpStatus.valueOf(code);
    }
    @Override public String name() {
        return status.getReason();
    }
    @Override public int code() {
        return status.getCode();
    }
}
''')
        def impl = argumentOf(context, 'mixintest.Test')
        def bean = impl.type.newInstance(200)

        expect:
        writeJson(jsonMapper, bean) == '200'

        def read = jsonMapper.readValue('200', typeUnderTest)
        read.name() == 'Ok'
        read.code() == 200
        read.getClass().name == 'mixintest.Another'

        cleanup:
        context.close()
    }

    void "test import with mixin - records"() {
        def context = buildContext('mixintest.Test','''
package mixintest;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.SerdeImport;

@SerdeImport(
    value = Test.class,
    mixin = TestMixin.class
)
class TestImport {}

public record Test(
    String name,
    int quantity) {
}

record TestMixin(
    @JsonProperty("n")
    String name,
    @JsonProperty("qty")
    int quantity
) {}
''')
        def bean = typeUnderTest.type.newInstance("test", 10)

        expect:
        writeJson(jsonMapper, bean) == '{"n":"test","qty":10}'

        def read = jsonMapper.readValue('{"n":"test","qty":15}', typeUnderTest)
        read.name() == 'test'
        read.quantity() == 15

        cleanup:
        context.close()
    }

    void "test import with mixin - constructors"() {
        def context = buildContext('mixintest.Test','''
package mixintest;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.SerdeImport;

@SerdeImport(
    value = Test.class,
    mixin = TestMixin.class
)
class TestImport {}

public class Test {
    private final String name;
    private final int quantity;
    Test(String name, int quantity) {
        this.name = name;
        this.quantity = quantity;
    }
    public String getName() {
        return name;
    }

    public int getQuantity() {
        return quantity;
    }
}

abstract class TestMixin {
    @JsonProperty("n")
    abstract String getName();

    @JsonProperty("qty")
    abstract int getQuantity();
}
''')
        def bean = typeUnderTest.type.newInstance("test", 10)

        expect:
        writeJson(jsonMapper, bean) == '{"n":"test","qty":10}'

        def read = jsonMapper.readValue('{"n":"test","qty":15}', typeUnderTest)
        read.getName() == 'test'
        read.getQuantity() == 15

        cleanup:
        context.close()
    }


    void "test import with mixin"() {
        def context = buildContext('mixintest.Test','''
package mixintest;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.SerdeImport;

@SerdeImport(
    value = Test.class,
    mixin = TestMixin.class
)
class TestImport {}

public class Test {
    private String name;
    private int quantity = 10;
    public void setName(String name) {
        this.name = name;
    }
    public String getName() {
        return name;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }
}

abstract class TestMixin {
    @JsonProperty("n")
    private String name;

    @JsonProperty("qty")
    public abstract int getQuantity();

    @JsonProperty("qty")
    public abstract void setQuantity(int quantity);
}
''', [name:'test'])

        expect:
        writeJson(jsonMapper, beanUnderTest) == '{"n":"test","qty":10}'

        def read = jsonMapper.readValue('{"n":"test","qty":15}', typeUnderTest)
        read.name == 'test'
        read.quantity == 15

        cleanup:
        context.close()
    }

    void "test import with mixin - annotation on non-property method"() {
        def context = buildContext('mixintest.Test','''
package mixintest;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.SerdeImport;

@SerdeImport(
    value = Test.class,
    mixin = TestMixin.class
)
class TestImport {}

public class Test {
    private String name;
    private int quantity = 10;

    public void name(String name) {
        this.name = name;
    }
    public String name() {
        return name;
    }

    public int quantity() {
        return quantity;
    }

    public void quantity(int quantity) {
        this.quantity = quantity;
    }
}

abstract class TestMixin {
    @JsonProperty("n")
    public abstract String name();

    @JsonProperty("n")
    public abstract void name(String name);

    @JsonProperty("qty")
    public abstract int quantity();

    @JsonProperty("qty")
    public abstract void quantity(int quantity);
}
''')
        def bean = typeUnderTest.type.newInstance()
        bean.name("test")
        bean.quantity(10)

        expect:
        writeJson(jsonMapper, bean) == '{"n":"test","qty":10}'

        def read = jsonMapper.readValue('{"n":"test","qty":15}', typeUnderTest)
        read.name() == 'test'
        read.quantity() == 15

        cleanup:
        context.close()
    }

    void "test import serde - both serialization and deserializer"() {
        given:
        def context = buildContext('importtest.Test','''
package importtest;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.SerdeImport;

@SerdeImport(Test.class)
class TestImport {}

public class Test {
    @JsonProperty("n")
    private String name;
    public void setName(String name) {
        this.name = name;
    }
    public String getName() {
        return name;
    }
}
''', [name:'test'])

        expect:
        writeJson(jsonMapper, beanUnderTest) == '{"n":"test"}'
        jsonMapper.readValue('{"n":"test"}', typeUnderTest).name == 'test'

        cleanup:
        context.close()
    }

    void "test import serde - ser only"() {
        given:
        def context = buildContext('importtest.Test','''
package importtest;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.SerdeImport;
import io.micronaut.serde.annotation.Serdeable;

@SerdeImport(
    value = Test.class,
    deserializable = false
)
class TestImport {}

public class Test {
    @JsonProperty("n")
    private String name;
    public void setName(String name) {
        this.name = name;
    }
    public String getName() {
        return name;
    }
}
''', [name:'test'])



        when:
        def result = writeJson(jsonMapper, beanUnderTest)

        then:
        result == '{"n":"test"}'

        when:
        jsonMapper.readValue('{"n":"test"}', typeUnderTest).name == 'test'

        then:
        def e = thrown(IntrospectionException)
    }

    void "test import with deserialize as"() {
        given:
        def context = buildContext('''
package mixindeser;

class DummyContext {}
''')

        HealthResult hr = HealthResult.builder("db", HealthStatus.DOWN)
                .details(Collections.singletonMap("foo", "bar"))
                .build()

        when:
        def result = writeJson(jsonMapper, hr)

        then:
        result == '{"name":"db","status":"DOWN","details":{"foo":"bar"}}'

        when:
        hr = jsonMapper.readValue(result, Argument.of(HealthResult))

        then:
        hr.name == 'db'
        hr.status == HealthStatus.DOWN


        cleanup:
        context.close()
    }

    void "test import with internal array containing nulls"() {
        def context = buildContext('importtest.Test','''
package importtest;

import io.micronaut.serde.annotation.SerdeImport;
import one.microstream.storage.restadapter.types.ViewerObjectDescription;
import com.fasterxml.jackson.annotation.JsonInclude;

@SerdeImport(
        value = ViewerObjectDescription.class,
         mixin = Test.ViewerObjectDescriptionMixin.class
)
public class Test {

    // A mixin to keep showing null values and empty lists
    @SuppressWarnings("DefaultAnnotationParam")
    @JsonInclude(JsonInclude.Include.ALWAYS)
    interface ViewerObjectDescriptionMixin {
    }
}
''')

        def bean = new ViewerObjectDescription().with(true) {
            it.setReferences(new ViewerObjectDescription[] {
                new ViewerObjectDescription(),
                null
            })
        }

        when:
        def result = writeJson(jsonMapper, bean)

        then:
        result == '{"objectId":null,"typeId":null,"length":null,"data":null,"references":[' +
                '{"objectId":null,"typeId":null,"length":null,"data":null,"references":null,"variableLength":null,"simplified":false},' +
                'null' +
                '],"variableLength":null,"simplified":false}'

        cleanup:
        context.close()
    }

    @Issue("https://github.com/micronaut-projects/micronaut-serialization/issues/687")
    void "test import serde with mixin and custom deserializer"() {
        given:
            def context = buildContext('customdeser.Box','''
package customdeser;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.annotation.SerdeImport;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import jakarta.inject.Singleton;
import java.io.IOException;
// third-party interface with factory method
public sealed interface Box<T> permits BoxImpl {
  T getThings();
  static <T> Box<T> factoryMethod(T things) { return new BoxImpl<>(things); }
}
// third-party internal implementation
final class BoxImpl<T> implements Box<T> {
  private final T things;
  BoxImpl(T things) { this.things = things; }
  @Override public T getThings() { return things; }
}
// custom builder
@Serdeable.Deserializable
class BoxBuilder<T> {
  private T things;
  public void setThings(T things) { this.things = things; }
  public Box<T> build() { return Box.factoryMethod(things); }
}
// custom deserializer
@Singleton
class BoxDeserializer implements Deserializer<Box<?>> {
  @Override public Box<?> deserialize(Decoder decoder, DecoderContext context, Argument<? super Box<?>> type) throws IOException {
    Argument<BoxBuilder> builderType = Argument.of(BoxBuilder.class, type.getTypeParameters());
    return context.findDeserializer(builderType).createSpecific(context, builderType).deserializeNullable(decoder, context, builderType).build();
  }
}
// serde import
@SerdeImport(value = Box.class, mixin = BoxMixin.class)
interface BoxSerdeImport {}
// serde mixin
@Serdeable.Deserializable(using = BoxDeserializer.class)
interface BoxMixin {}
// container with type params
@Serdeable.Deserializable
class Generic<T> {
  private final T stuff;
  public Generic(T stuff) { this.stuff = stuff; }
  public T getStuff() { return stuff; }
}
// container without type params
@Serdeable.Deserializable
class Container {
  private final Box<Generic<Long>> contents;
  public Container(Box<Generic<Long>> contents) { this.contents = contents; }
  public Box<Generic<Long>> getContents() { return contents; }
}
''')
            def typeUnderTestWithTypeParams = Argument.of(typeUnderTest.type, Argument.ofTypeVariable(Long.class, 'T'))

        expect: "type under test can be serialized"
            writeJson(jsonMapper, typeUnderTest.type.factoryMethod(123L)) == '{"things":123}'

        and: "type under test can be deserialized without type params"
            jsonMapper.readValue('{"things":"OK"}', typeUnderTest).things == 'OK'

        and: "type under test can be deserialized with type params"
            jsonMapper.readValue('{"things":123}', typeUnderTestWithTypeParams).things instanceof Long

        when: "type under test is deserialized inside another serdeable object"
            def container = jsonMapper.readValue('{"contents":{"things":{"stuff":123}}}', argumentOf(context, 'customdeser.Container'))

        then: "type params are honored"
            container.class.name == 'customdeser.Container'
            container.contents.class.name == 'customdeser.BoxImpl'
            container.contents.things.class.name == 'customdeser.Generic'
            container.contents.things.stuff instanceof Long

        cleanup:
            context.close()
    }

    void "test custom deserializer"() {
        given:
            def context = buildContext('custom.CustomValue','''
package custom;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.annotation.SerdeImport;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import jakarta.inject.Singleton;
import java.io.IOException;

@Serdeable
record CustomValue(
        @Serdeable.Deserializable(using = CustomSerde.class)
        @JsonProperty("value")
        @NonNull
        Integer value
) {
    @JsonCreator
    public CustomValue {
    }
}

@Singleton
class CustomSerde implements Deserializer<Integer> {
    @Override
    public Integer getDefaultValue(DecoderContext ignoredContext, Argument<? super Integer> ignoredType) {
        return -2;
    }

    @Override
    public Integer deserialize(Decoder decoder, DecoderContext context, Argument<? super Integer> type) throws IOException {
        return decoder.decodeInt();
    }

    @Override
    public Integer deserializeNullable(Decoder decoder, DecoderContext context, Argument<? super Integer> type) throws IOException {
        if (decoder.decodeNull()) {
            return -1;
        }
        return decoder.decodeInt();
    }
}
''')

        expect:
            jsonMapper.readValue('{"value":1}', typeUnderTest).value() == 1
            jsonMapper.readValue('{"value":null}', typeUnderTest).value() == -1
            jsonMapper.readValue('{}', typeUnderTest).value() == -2

        cleanup:
            context.close()
    }
}
