package io.micronaut.serde.jackson

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import io.micronaut.core.beans.exceptions.IntrospectionException
import io.micronaut.core.type.Argument
import io.micronaut.health.HealthStatus
import io.micronaut.management.health.indicator.HealthResult
import one.microstream.storage.restadapter.types.ViewerObjectDescription
import spock.lang.Requires

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

@Serdeable.Deserializable(as = Another.class)
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

    @Requires({ jvm.isJava17Compatible() })
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
}
