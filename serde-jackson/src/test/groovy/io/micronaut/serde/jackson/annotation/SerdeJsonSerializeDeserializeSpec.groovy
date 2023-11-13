package io.micronaut.serde.jackson.annotation


import io.micronaut.serde.jackson.JsonSerializeDeserializeSpec

class SerdeJsonSerializeDeserializeSpec extends JsonSerializeDeserializeSpec {

    void 'test errors'() {
        when:
            buildContext('test.Test', """
package test;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.micronaut.serde.annotation.Serdeable;
import java.util.LinkedList;
import java.util.List;
import java.time.LocalDate;

@Serdeable.Deserializable(as = LinkedList.class)
@Serdeable.Serializable(as = LocalDate.class)
public interface Test {}

""")
        then:
        def e = thrown(RuntimeException)
        e.message.contains "Type to serialize as [java.time.LocalDate], must be a subtype of the annotated type: test.Test"
    }

    void 'test json deserialize on collection'() {
        given:
        def context = buildContext('test.Test', """
package test;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.micronaut.serde.annotation.Serdeable;
import java.util.LinkedList;
import java.util.List;

@Serdeable.Deserializable
record Test(
    @JsonDeserialize(as = LinkedList.class) List<Integer> list
) {}

""")

        when:
        def result = jsonMapper.readValue('{"list": [1, 2, 3]}', typeUnderTest);
        then:
        result.getClass().name == 'test.Test'
        result.list instanceof LinkedList
        result.list == [1, 2, 3] as LinkedList

        cleanup:
        context.close()
    }

    void 'test basic json deserialize on collection'() {
        given:
        def context = buildContext('test.Test', """
package test;

import io.micronaut.serde.annotation.Serdeable;
import java.util.LinkedList;
import java.util.List;

@Serdeable.Deserializable
record Test(
    @Serdeable.Deserializable(as = LinkedList.class) List<Integer> list
) {}

""")

        when:
        def result = jsonMapper.readValue('{"list": [1, 2, 3]}', typeUnderTest);
        then:
        result.getClass().name == 'test.Test'
        result.list instanceof LinkedList
        result.list == [1, 2, 3] as LinkedList

        cleanup:
        context.close()
    }
}
