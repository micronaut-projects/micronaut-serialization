package io.micronaut.serde.bson

class BsonIdSpec extends BsonCompileSpec {

    void "test @BsonId on field"() {
        given:
            def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;

import org.bson.codecs.pojo.annotations.BsonId;import org.bson.codecs.pojo.annotations.BsonProperty;

@Serdeable
class Test {
    private String abc;
    @BsonId
    private String value;
    
    public void setAbc(String abc) {
        this.abc = abc;
    }
    
    public String getAbc() {
        return abc;
    }

    public void setValue(String value) {
        this.value = value;
    } 
    public String getValue() {
        return value;
    }
}
""", [value: 'test', abc: 'xyz'])

        expect:
            asBsonJsonString(beanUnderTest) == '{"abc": "xyz", "_id": "test"}'

        cleanup:
            context.close()
    }

}
