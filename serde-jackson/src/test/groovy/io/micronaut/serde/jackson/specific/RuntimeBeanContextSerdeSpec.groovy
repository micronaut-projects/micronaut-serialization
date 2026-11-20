package io.micronaut.serde.jackson.specific

import io.micronaut.context.ApplicationContext
import io.micronaut.serde.ObjectMapper
import spock.lang.Specification

import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * {@link SecretSerde} requires the context of the call it serves to be the context it was created with. The runtime
 * bean serdes keep the property serdes of a type between documents.
 */
@spock.lang.Issue("https://github.com/micronaut-projects/micronaut-serialization/issues/1521")
class RuntimeBeanContextSerdeSpec extends Specification {

    private static final Map<String, Object> RUNTIME = [
        'micronaut.serde.serialization.disable-generated-serializer'    : true,
        'micronaut.serde.deserialization.disable-generated-deserializer': true
    ]

    void "a property with a contextual serde is written with the context of each document by the runtime serializer"() {
        given:
        def context = ApplicationContext.run(RUNTIME)
        def mapper = context.getBean(ObjectMapper)

        expect:
        (1..3).every { mapper.writeValueAsString(new SecretHolder(new Secret("s$it"))) == "{\"secret\":\"s$it\"}" }

        cleanup:
        context.close()
    }

    void "a property with a contextual serde is read with the context of each document by the runtime deserializer"() {
        given:
        def context = ApplicationContext.run(RUNTIME)
        def mapper = context.getBean(ObjectMapper)

        expect:
        (1..3).every { mapper.readValue("{\"secret\":\"s$it\"}", SecretHolder) == new SecretHolder(new Secret("s$it")) }

        cleanup:
        context.close()
    }

    void "a collection of a type with a contextual serde is written and read with the context of each document"() {
        given:
        def context = ApplicationContext.run(RUNTIME)
        def mapper = context.getBean(ObjectMapper)

        expect:
        (1..3).every {
            def json = mapper.writeValueAsString(new SecretListHolder(secrets: [new Secret("a$it"), new Secret("b$it")]))
            json == "{\"secrets\":[\"a$it\",\"b$it\"]}" && mapper.readValue(json, SecretListHolder).secrets == [new Secret("a$it"), new Secret("b$it")]
        }

        cleanup:
        context.close()
    }

    @io.micronaut.serde.annotation.Serdeable
    static class SecretListHolder {
        List<Secret> secrets
    }

    void "documents written and read in parallel each use their own contextual serdes"() {
        given:
        def context = ApplicationContext.run(RUNTIME)
        def mapper = context.getBean(ObjectMapper)
        def pool = Executors.newFixedThreadPool(8)

        when:
        def results = pool.invokeAll((1..400).collect { i ->
            (Callable<Boolean>) {
                mapper.writeValueAsString(new SecretHolder(new Secret("v$i"))) == "{\"secret\":\"v$i\"}" &&
                    mapper.readValue("{\"secret\":\"v$i\"}", SecretHolder) == new SecretHolder(new Secret("v$i"))
            }
        }).collect { it.get() }

        then:
        results.every()

        cleanup:
        pool.shutdown()
        context.close()
    }
}
