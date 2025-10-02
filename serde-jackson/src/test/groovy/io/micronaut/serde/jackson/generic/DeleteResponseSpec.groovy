package io.micronaut.serde.jackson.generic

import io.micronaut.core.type.Argument
import io.micronaut.serde.ObjectMapper
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class DeleteResponseSpec extends Specification {

    @Inject
    ObjectMapper objectMapper

    void testNamespaceResponse() {
        given:
        String namespaceResponse = """
        {"kind":"Namespace","apiVersion":"v1","spec":{"finalizers":["kubernetes"]},"status":{"phase":"Active"}}
        """

        when:
        DeleteResponse<V1Namespace> response = objectMapper.readValue(namespaceResponse, Argument.of(DeleteResponse.class, Argument.ofTypeVariable(V1Namespace.class, "T")))

        then:
        response.object() instanceof V1Namespace
        response.status() == null
    }

    void testStatusResponse() {
        given:
        String statusResponse = """
        {"kind":"Status","apiVersion":"v1","status":"Failure","message":"too old resource version: 1200 (184080)","reason":"Expired"}
        """

        when:
        DeleteResponse<V1Namespace> response = objectMapper.readValue(statusResponse, Argument.of(DeleteResponse.class, Argument.ofTypeVariable(V1Namespace.class, "T")))

        then:
        response.object() == null
        response.status() instanceof V1Status
        response.status().reason == "Expired"
    }

}


