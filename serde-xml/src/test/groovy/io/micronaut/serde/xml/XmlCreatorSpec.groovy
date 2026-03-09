package io.micronaut.serde.xml

import io.micronaut.core.type.Argument

class XmlCreatorSpec extends XmlCompileSpec {

    void "test default constructor 1"() {
        given:
            def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;

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
            def obj = xmlMapper.readValue('<Test><value1>A</value1><value2>B</value2><value3>C</value3></Test>'.bytes, Argument.of(typeUnderTest.type))
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
            def obj = xmlMapper.readValue('<Test><value1>A</value1><value2>B</value2><value3>C</value3></Test>'.bytes, Argument.of(typeUnderTest.type))
        then:
            obj.getValue1() == "A"
            obj.getValue2() == "B"
            obj.getValue3() == "C"
        cleanup:
            context.close()
    }

    void "test @Creator constructor"() {
        given:
            def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.core.annotation.Creator;

@Serdeable
class Test {
    private final String value1;
    private final String value2;
    private final String value3;

    public Test(String value1, String value2) {
        this(value1, value2, null);
    }

    @Creator
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
            def obj = xmlMapper.readValue('<Test><value1>A</value1><value2>B</value2><value3>C</value3></Test>'.bytes, Argument.of(typeUnderTest.type))
        then:
            obj.getValue1() == "A"
            obj.getValue2() == "B"
            obj.getValue3() == "C"
        cleanup:
            context.close()
    }

}
