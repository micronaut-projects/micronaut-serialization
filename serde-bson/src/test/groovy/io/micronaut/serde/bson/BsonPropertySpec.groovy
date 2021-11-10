package io.micronaut.serde.bson

class BsonPropertySpec extends BsonCompileSpec {

    void "test @BsonProperty on field"() {
        given:
            def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;

import org.bson.codecs.pojo.annotations.BsonProperty;

@Serdeable
class Test {
    @BsonProperty(value = "other")
    private String value;
    
    public void setValue(String value) {
        this.value = value;
    } 
    public String getValue() {
        return value;
    }
}
""", [value: 'test'])

        expect:
            asBsonJsonString(beanUnderTest) == '{"other": "test"}'

        cleanup:
            context.close()
    }

}
