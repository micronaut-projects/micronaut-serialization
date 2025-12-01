package io.micronaut.serde.jackson.generic.scenario3

import io.micronaut.core.type.Argument
import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class SerdeSpec extends Specification {

    @Inject
    ObjectMapper objectMapper

    void testAddedEventDeserialization() {
        given:
            String addedEvent = """
            {"type":"ADDED","object":{"kind":"Namespace","apiVersion":"v1","spec":{"finalizers":["kubernetes"]},"status":{"phase":"Active"}}}
            """;

        when:
            WatchEvent<V1Namespace> namespaceWatchEvent = objectMapper.readValue(addedEvent, Argument.of(WatchEvent.class, Argument.ofTypeVariable(V1Namespace.class, "T")));

        then:
            namespaceWatchEvent.object() instanceof V1Namespace
    }

    void testErrorEventDeserialization() {
        given:
            String errorEvent = """
            {"type":"ERROR","status":{"kind":"Status","apiVersion":"v1","status":"Failure","message":"too old resource version: 1200 (184080)","reason":"Expired"}}
            """;

        when:
            WatchEvent<V1Namespace> namespaceWatchEvent = objectMapper.readValue(errorEvent, Argument.of(WatchEvent.class, Argument.ofTypeVariable(V1Namespace.class, "T")));

        then:
            namespaceWatchEvent.object() == null
            namespaceWatchEvent.status() instanceof V1Status
            namespaceWatchEvent.status().reason == "Expired"
    }

}


