package io.micronaut.serde.bson

class BsonCreatorSpec extends BsonCompileSpec {

    void "test default constructor 1"() {
        given:
            def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;

import org.bson.codecs.pojo.annotations.BsonProperty;

@Serdeable
class Test {
    private final String value1;
    private final String value2;
    private final String value3;
    
    public Test(String value1, String value2) {
        this(value1, value2, null);
    }
    
    public Test(String value1, String value2, String value3) {
        this.value1 = value1;
        this.value2 = value2;
        this.value3 = value3;
    }
    
    public String getValue1() {
        return value1;
    }
    
    public String getValue2() {
        return value2;
    }
    
    public String getValue3() {
        return value3;
    }

}
""")
        when:
            def obj = objectFromBsonJson('{"value1": "A", "value2": "B", "value3": "C"}', typeUnderTest.getType())
        then:
            obj.getValue1() == "A"
            obj.getValue2() == "B"
            !obj.getValue3()
        cleanup:
            context.close()
    }

    void "test default constructor 2"() {
        given:
            def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;

import org.bson.codecs.pojo.annotations.BsonProperty;

@Serdeable
class Test {
    private final String value1;
    private final String value2;
    private final String value3;
    
    public Test(String value1, String value2, String value3) {
        this.value1 = value1;
        this.value2 = value2;
        this.value3 = value3;
    }
    
    public Test(String value1, String value2) {
        this(value1, value2, null);
    }
    
    public String getValue1() {
        return value1;
    }
    
    public String getValue2() {
        return value2;
    }
    
    public String getValue3() {
        return value3;
    }

}
""")
        when:
            def obj = objectFromBsonJson('{"value1": "A", "value2": "B", "value3": "C"}', typeUnderTest.getType())
        then:
            obj.getValue1() == "A"
            obj.getValue2() == "B"
            obj.getValue3() == "C"
        cleanup:
            context.close()
    }

    void "test @BsonCreator constructor"() {
        given:
            def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;

import org.bson.codecs.pojo.annotations.BsonCreator;
import org.bson.codecs.pojo.annotations.BsonProperty;

@Serdeable
class Test {
    private final String value1;
    private final String value2;
    private final String value3;
    
    public Test(String value1, String value2) {
        this(value1, value2, null);
    }
    
    @BsonCreator
    public Test(String value1, String value2, String value3) {
        this.value1 = value1;
        this.value2 = value2;
        this.value3 = value3;
    }
    
    public String getValue1() {
        return value1;
    }
    
    public String getValue2() {
        return value2;
    }
    
    public String getValue3() {
        return value3;
    }

}
""")
        when:
            def obj = objectFromBsonJson('{"value1": "A", "value2": "B", "value3": "C"}', typeUnderTest.getType())
        then:
            obj.getValue1() == "A"
            obj.getValue2() == "B"
            obj.getValue3() == "C"
        cleanup:
            context.close()
    }

}
