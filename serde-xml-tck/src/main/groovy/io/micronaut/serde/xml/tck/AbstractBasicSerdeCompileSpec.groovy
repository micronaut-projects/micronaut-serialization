/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.serde.xml.tck

import io.micronaut.core.type.Argument
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
        def read = xmlMapper.readValue(bytes, typeUnderTest)
        typeUnderTest.type.isInstance(read)
        read.value == data.value
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
        def context = buildReadContext('test.Test', """
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


    void "test @JsonIgnore on field"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;

import com.fasterxml.jackson.annotation.JsonIgnore;

@Serdeable
class Test {
    private String value;
    @JsonIgnore
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
""", [value: 'test', ignored: true])

        when:
        def bytes = xmlMapper.writeValueAsBytes(beanUnderTest)

        then:
        new String(bytes) == '<Test><value>test</value></Test>'

        cleanup:
        context.close()

    }

    void "test @JsonIgnore on method"() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import com.fasterxml.jackson.annotation.JsonIgnore;

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

    @JsonIgnore
    public boolean isIgnored() {
        return ignored;
    }
}
""", [value: 'test'])
        when:
        def bytes = xmlMapper.writeValueAsBytes(beanUnderTest)

        then:
        new String(bytes) == '<Test><value>test</value></Test>'

        cleanup:
        context.close()

    }

    void "test skip unknown value on read"() {
        given:
        def context = buildReadContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
class Test {
    private String value;

    public void setValue(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
""")

        when:
        def read = xmlMapper.readValue('<Test><value>hello</value><ignored>world</ignored></Test>', typeUnderTest)

        then:
        read.value == 'hello'

        cleanup:
        context.close()
    }

    void "test default constructor 1"() {
        given:
        def context = buildReadContext('test.Test', """
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
        def context = buildReadContext('test.Test', """
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
        def context = buildReadContext('test.Test', """
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

    void "missing list"() {
        given:
        def context = buildReadContext('test.ObjectWithArray', """
package test;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
class ObjectWithArray {
    private List<SomeObject> vals;

    List<SomeObject> getVals() {
        return vals;
    }

    void setVals(List<SomeObject> vals) {
        this.vals = vals;
    }
}

@Serdeable
class SomeObject {
    private String val;

    String getVal() {
        return val;
    }

    void setVal(String val) {
        this.val = val;
    }
}
""")
        when:
        def obj = xmlMapper.readValue('<ObjectWithArray/>', typeUnderTest)
        then:
        obj
        obj.vals == null
        cleanup:
        context.close()
    }

    void "missing list - constructor"() {
        given:
        def context = buildReadContext('test.ObjectWithArrayConstructor', """
package test;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
class ObjectWithArrayConstructor {
    private final List<SomeObject> vals;

    ObjectWithArrayConstructor(List<SomeObject> vals) {
        this.vals = vals;
    }

    List<SomeObject> getVals() {
        return vals;
    }
}

@Serdeable
class SomeObject {
    private String val;

    String getVal() {
        return val;
    }

    void setVal(String val) {
        this.val = val;
    }
}
""")
        when:
        def obj = xmlMapper.readValue('<ObjectWithArrayConstructor/>', typeUnderTest)
        then:
        obj
        obj.vals == null
        cleanup:
        context.close()
    }

    void "missing list - record"() {
        given:
        def context = buildReadContext('test.ObjectWithArrayRecord', """
package test;

import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
record ObjectWithArrayRecord(List<SomeObject> vals) {
}

@Serdeable
class SomeObject {
    private String val;

    String getVal() {
        return val;
    }

    void setVal(String val) {
        this.val = val;
    }
}
""")
        when:
        def obj = xmlMapper.readValue('<ObjectWithArrayRecord/>', typeUnderTest)
        then:
        obj
        obj.vals() == null
        cleanup:
        context.close()
    }

    void "missing list - required"() {
        given:
        def context = buildReadContext('test.ObjectWithArrayRequired', """
package test;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.serde.annotation.Serdeable;

import java.util.List;

@Serdeable
record ObjectWithArrayRequired(@JsonProperty(required = true) List<SomeObject> vals) {
}

@Serdeable
class SomeObject {
    private String val;

    String getVal() {
        return val;
    }

    void setVal(String val) {
        this.val = val;
    }
}
""")
        when:
        xmlMapper.readValue('<ObjectWithArrayRequired/>', typeUnderTest)
        then:
        def e = thrown(Exception)
        e.message.contains("Required constructor parameter") || e.message.contains("Missing required creator property")
        cleanup:
        context.close()
    }





}
