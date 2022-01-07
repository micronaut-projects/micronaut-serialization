package io.micronaut.serde.jackson

import com.amazonaws.services.lambda.runtime.events.SQSEvent
import io.micronaut.context.ApplicationContext
import io.micronaut.serde.exceptions.SerdeException
import spock.lang.PendingFeature
import spock.lang.Requires

class SerdeImportSpec extends JsonCompileSpec {

    @PendingFeature(reason = "Not possible to dynamically annotate inner classes due to a bug in core")
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
    deser = @Serdeable.Deserializable(enabled = false)
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
        def e = thrown(SerdeException)
    }
}
