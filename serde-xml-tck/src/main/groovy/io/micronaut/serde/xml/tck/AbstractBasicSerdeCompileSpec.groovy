package io.micronaut.serde.xml.tck

import io.micronaut.http.HttpStatus
import spock.lang.Ignore
import spock.lang.Unroll

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets;

class AbstractBasicSerdeCompileSpec extends AbstractXmlCompileSpec {

    @Unroll
    void "test basic type #type"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    private $type.name value;
    public void setValue($type.name value) {
        this.value = value;
    }
    public $type.name getValue() {
        return value;
    }
}
""", data)
        expect:
        def bytes = xmlMapper.writeValueAsBytes(beanUnderTest)
        //def read = xmlMapper.readValue(bytes, typeUnderTest)
        //typeUnderTest.type.isInstance(read)
        //read.value == data.value
        new String(bytes) == result

        cleanup:
        context.close()

        where:
        type       | data                                   | result
        BigDecimal | [value: 10.1]                          | '<Test><value>10.1</value></Test>'
        BigInteger | [value: BigInteger.valueOf(10l)]       | '<Test><value>10</value></Test>'
        String     | [value: "Test"]                        | '<Test><value>Test</value></Test>'
        boolean    | [value: true]                          | '<Test><value>true</value></Test>'
        byte       | [value: 10]                            | '<Test><value>10</value></Test>'
        short      | [value: 10]                            | '<Test><value>10</value></Test>'
        int        | [value: 10]                            | '<Test><value>10</value></Test>'
        long       | [value: 10]                            | '<Test><value>10</value></Test>'
        double     | [value: 10.1d]                         | '<Test><value>10.1</value></Test>'
        float      | [value: 10.1f]                         | '<Test><value>10.1</value></Test>'
        char       | [value: 'a' as char]                   | '<Test><value>a</value></Test>'
        char       | [value: 97 as char]                    | '<Test><value>a</value></Test>'
        //wrappers
        Boolean    | [value: true]                          | '<Test><value>true</value></Test>'
        Byte       | [value: 10]                            | '<Test><value>10</value></Test>'
        Short      | [value: 10]                            | '<Test><value>10</value></Test>'
        Integer    | [value: 10]                            | '<Test><value>10</value></Test>'
        Long       | [value: 10]                            | '<Test><value>10</value></Test>'
        Double     | [value: 10.1d]                         | '<Test><value>10.1</value></Test>'
        Float      | [value: 10.1f]                         | '<Test><value>10.1</value></Test>'
        Character  | [value: 'a' as char]                   | '<Test><value>a</value></Test>'
        Character  | [value: 97 as char]                   | '<Test><value>a</value></Test>'
        HttpStatus | [value: HttpStatus.ACCEPTED]           | '<Test><value>ACCEPTED</value></Test>'
        CharSequence | [value: "Xyz"]                       | '<Test><value>Xyz</value></Test>'
        // other common classes
        URI        | [value: URI.create("https://foo.com")] | '<Test><value>https://foo.com</value></Test>'
        URL        | [value: new URL("https://foo.com")]    | '<Test><value>https://foo.com</value></Test>'
        Charset    | [value: StandardCharsets.UTF_8]        | '<Test><value>UTF-8</value></Test>'
        TimeZone   | [value: TimeZone.getTimeZone("GMT")]   | '<Test><value>GMT</value></Test>'
        Locale     | [value: Locale.CANADA_FRENCH]          | '<Test><value>fr-CA</value></Test>'
    }

    //Todo: read value from tree

    @Unroll
    void "test basic type #type missing value"() {
        given:
        def context = buildContext("""
package test;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    @org.jspecify.annotations.Nullable
    private $type value;
    public void setValue($type value) {
        this.value = value;
    }
    public $type getValue() {
        return value;
    }
}
""")

        def typeUnderTest = argumentOf(context, 'test.Test')

        expect:
        def read = xmlMapper.readValue('<Test></Test>', typeUnderTest)
        typeUnderTest.type.isInstance(read)
        read.value == defaultValue

        cleanup:
        context?.close()

        where:
        type << ['Integer', 'int', 'int[]']
        defaultValue << [null, 0, null]
    }



}
