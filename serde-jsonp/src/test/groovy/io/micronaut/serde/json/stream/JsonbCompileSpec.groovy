package io.micronaut.serde.json.stream

import io.micronaut.serde.AbstractJsonCompileSpec

class JsonbCompileSpec extends AbstractJsonCompileSpec {

    void 'test read/write jsonb entity'() {
        given:
        def context = buildContext("""
package test;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.json.bind.annotation.JsonbPropertyOrder;
import jakarta.json.bind.annotation.JsonbNillable;
import jakarta.json.bind.annotation.JsonbCreator;
@JsonbPropertyOrder({"name", "price", "dateCreated"}) 
@Serdeable
@JsonbNillable
class Test extends io.micronaut.serde.json.stream.JsonbTest {
    @JsonbCreator
    Test(String name) {
        super(name);
    }
}
""")
        def o = newInstance(context, 'test.Test', "test")
        when:
        def result = writeJson(jsonMapper, o)

        then:
        result == '{"n":"test","price":"$1.1","dateCreated":"011021","other":null}'


        cleanup:
        context.close()
    }
}
