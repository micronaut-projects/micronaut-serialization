package io.micronaut.serde.jackson
/**
 *
 * @author gkrocher
 */
class JacksonCustomSerializerSpec extends JsonCompileSpec {
	void "test custom serializer type level"() {
        given:
        def context = buildContext("test.Test", """
package test;

import io.micronaut.serde.annotation.Serdeable.Serializable;
import io.micronaut.serde.*;
import io.micronaut.core.type.*;
import jakarta.inject.*;
import java.io.IOException;

@Serializable(using=TestSerializer.class)
class Test {
    public int x, y;
}

@Singleton
class TestSerializer implements Serializer<Test> {


    public void serialize(Encoder encoder,
                          EncoderContext context,
                          Test value,
                          Argument<? extends Test> type) throws IOException {
        Encoder array = encoder.encodeArray(type);
        array.encodeInt(value.x);
        array.encodeInt(value.y);
        array.finishStructure();
    }
}
""")
        def object = typeUnderTest.type.newInstance()
        object.x = 50
        object.y = 100

        expect:
        writeJson(jsonMapper, object) == '[50,100]'

        cleanup:
        context.close();
    }

    void "test custom serializer field level"() {
        given:
        def context = buildContext("test.Test", """
package test;

import io.micronaut.serde.annotation.Serdeable.Serializable;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.*;
import io.micronaut.core.type.*;
import jakarta.inject.*;
import java.io.IOException;
import io.micronaut.context.annotation.Primary;
import io.micronaut.core.annotation.Introspected;

@Serdeable
@Introspected(accessKind = Introspected.AccessKind.FIELD)
class Test {
    @Serializable(using=FooReverseSerializer.class)
    public Foo foo;
}

class Foo {
    public int x, y;
}

@Singleton
@Primary
class FooSerializer implements Serializer<Foo> {


    public void serialize(Encoder encoder,
                          EncoderContext context,
                          Foo value,
                          Argument<? extends Foo> type) throws IOException {
        Encoder array = encoder.encodeArray(type);
        array.encodeInt(value.x);
        array.encodeInt(value.y);
        array.finishStructure();
    }
}

@Singleton
class FooReverseSerializer implements Serializer<Foo> {


    public void serialize(Encoder encoder,
                          EncoderContext context,
                          Foo value,
                          Argument<? extends Foo> type) throws IOException {
        Encoder array = encoder.encodeArray(type);
        array.encodeInt(value.y);
        array.encodeInt(value.x);        
        array.finishStructure();
    }
}
""")
        def fooClass = argumentOf(context,"test.Foo").type
        def object = typeUnderTest.type.newInstance()
        def foo = fooClass.newInstance()
        foo.x = 50
        foo.y = 100
        object.foo = foo;

        expect:
        writeJson(jsonMapper, object) == '{"foo":[100,50]}'

        cleanup:
        context.close();
    }
}

