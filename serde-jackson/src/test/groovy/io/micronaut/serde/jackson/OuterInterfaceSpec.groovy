package io.micronaut.serde.jackson

import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.jackson.outerinterface.AbstractClassCommandWorking
import io.micronaut.serde.jackson.outerinterface.AbstractClassCommandWorkingToo
import io.micronaut.serde.jackson.outerinterface.ClassCommandBroken
import io.micronaut.serde.jackson.outerinterface.ClassCommandBrokenToo
import io.micronaut.serde.jackson.outerinterface.ClassCommandWorking
import io.micronaut.serde.jackson.outerinterface.RecordCommandBroken
import io.micronaut.serde.jackson.outerinterface.RecordCommandBrokenToo
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class OuterInterfaceSpec extends Specification {
    @Inject ObjectMapper objectMapper

    void "test outer interface with abstract method with inner class"() throws IOException {
        when:
        String printSerialized = objectMapper.writeValueAsString(new ClassCommandWorking.PrintCommand("foo"))
        then:
        printSerialized == '{"type":"print","foo":"foo","fooBar":"foobar"}'
        objectMapper.readValue(
                printSerialized,
                ClassCommandWorking.class)
    }

    void "test outer interface with default method with inner class"() throws IOException {
        when:
        String printSerialized = objectMapper.writeValueAsString(new ClassCommandBroken.PrintCommand("foo"))

        then:
        printSerialized == '{"type":"print","foo":"foo","fooBar":"foobar"}'
        objectMapper.readValue(
                printSerialized,
                ClassCommandBroken.class)
    }

    void "test outer interface no methods with inner class"() throws IOException {
        when:
        String printSerialized = objectMapper.writeValueAsString(new ClassCommandBrokenToo.PrintCommand("foo"))

        then:
        printSerialized == '{"type":"print","foo":"foo"}'
        objectMapper.readValue(
                printSerialized,
                ClassCommandBrokenToo.class)
    }

    void "test outer interface with abstract method with record"() throws IOException {
        when:
        String printSerialized = objectMapper.writeValueAsString(new RecordCommandBroken.PrintCommand("foo"))

        then:
        printSerialized == '{"type":"print","foo":"foo","fooBar":"test"}'
        objectMapper.readValue(
                printSerialized,
                RecordCommandBroken.class)
    }

    void "test outer interface with record"() throws IOException {
        when:
        String printSerialized = objectMapper.writeValueAsString(new RecordCommandBrokenToo.PrintCommand("foo"))

        then:
        printSerialized == '{"type":"print","foo":"foo"}'
        objectMapper.readValue(
                printSerialized,
                RecordCommandBrokenToo.class)
    }

    void "test abstract with inner class"() throws IOException {
        when:
        String printSerialized = objectMapper.writeValueAsString(new AbstractClassCommandWorking.PrintCommand("foo"))

        then:
        printSerialized == '{"type":"print","foo":"foo"}'
        objectMapper.readValue(
                printSerialized,
                AbstractClassCommandWorking.class)
    }

    void "test abstract class with abstract method with inner class"() throws IOException {
        when:
        String printSerialized = objectMapper.writeValueAsString(new AbstractClassCommandWorkingToo.PrintCommand("foo"))

        then:
        printSerialized == '{"type":"print","foo":"foo","fooBar":"foobar"}'
        objectMapper.readValue(
                printSerialized,
                AbstractClassCommandWorkingToo.class)
    }
}
