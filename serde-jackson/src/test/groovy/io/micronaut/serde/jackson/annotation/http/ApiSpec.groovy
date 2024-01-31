package io.micronaut.serde.jackson.annotation.http

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.annotation.Client
import io.micronaut.json.JsonMapper
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.serde.jackson.JsonCompileSpec
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest
class ApiSpec extends Specification {

    @Inject
    ApiClient apiClient

    void "test simpleList"() {
        when:
            var simpleList = apiClient.simpleList();
        then:
            simpleList[0].name() == "test-1"
            simpleList[1].name() == "test-2"
    }

    void "test wrappedList"() {
        when:
            var wrappedList = apiClient.wrappedList();
        then:
            wrappedList.content()[0].name() == "test-1"
            wrappedList.content()[1].name() == "test-2"
    }

    void "test wrappedNested"() {
        when:
            var wrappedNested = apiClient.wrappedNested();
        then:
            wrappedNested.content().content().name() == "test-1"
    }

    void "test wrappedSimple"() {
        when:
            var wrappedSimple = apiClient.wrappedSimple();
        then:
            wrappedSimple.content().name() == "test-1"
    }

}

