package io.micronaut.serde.bson

class BsonIgnoreSpec extends BsonCompileSpec {

    void "test @BsonIgnore on field"() {
        given:
            def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;

import org.bson.codecs.pojo.annotations.BsonIgnore;

@Serdeable
class Test {
    private String value;
    @BsonIgnore
    private boolean ignored;
    public void setValue(String value) {
        this.value = value;
    } 
    public String getValue() {
        return value;
    }
    
    public void setIgnored(boolean b) {
        this.ignored = b;
    }
    
    public boolean isIgnored() {
        return ignored;
    }
}
""", [value: 'test'])

        expect:
            asBsonJsonString(beanUnderTest) == '{"value": "test"}'

        cleanup:
            context.close()

    }

    void "test @BsonIgnore on method"() {
        given:
            def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import org.bson.codecs.pojo.annotations.BsonIgnore;

@Serdeable
class Test {
    private String value;
    
    private boolean ignored;
    public void setValue(String value) {
        this.value = value;
    }
    
    public String getValue() {
        return value;
    }
    
    public void setIgnored(boolean b) {
        this.ignored = b;
    }
    
    @BsonIgnore
    public boolean isIgnored() {
        return ignored;
    }
}
""", [value: 'test'])
        expect:
            asBsonJsonString(beanUnderTest) == '{"value": "test"}'

        cleanup:
            context.close()

    }

}
