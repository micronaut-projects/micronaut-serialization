package io.micronaut.serde.jackson

import com.fasterxml.jackson.annotation.JsonAnyGetter
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.annotation.Nullable
import io.micronaut.serde.ObjectMapper
import io.micronaut.serde.annotation.Serdeable
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.PendingFeature
import spock.lang.Specification

@MicronautTest
class JsonAnyGetterSpec extends Specification {

    @Inject
    ObjectMapper serdeObjectMapper

    @Inject
    com.fasterxml.jackson.databind.ObjectMapper jacksonObjectMapper

    void "JsonAnyGetter works with object Annotated with @Serdeable with Serde Object Mapper"() {
        given:
        TokenSerdeable token = new TokenSerdeable(Collections.singletonMap("roles", Collections.singletonList("ADMIN")))

        when:
        String json = serdeObjectMapper.writeValueAsString(token)
        then:
        '{"roles":["ADMIN"]}' == json
    }

    void "JsonAnyGetter works with an object not annotated with @Introspected neither with @Serdeable with Jackson ObjectMapper"() {
        given:
        TokenNoIntrospection token = new TokenNoIntrospection(Collections.singletonMap("roles", Collections.singletonList("ADMIN")))

        when:
        String json = jacksonObjectMapper.writeValueAsString(token)
        then:
        '{"roles":["ADMIN"]}' == json
    }

    @PendingFeature
    void "JsonAnyGetter works with an object annotated with @Introspected with Jackson ObjectMapper"() {
        given:
        Token token = new Token(Collections.singletonMap("roles", Collections.singletonList("ADMIN")))

        when:
        String json = jacksonObjectMapper.writeValueAsString(token)
        then:
        '{"roles":["ADMIN"]}' == json
    }

    static class TokenNoIntrospection {
        @NonNull
        private final Map<String, Object> extensions = new HashMap<>()

        TokenNoIntrospection(@Nullable Map<String, Object> extensions) {
            if (extensions != null) {
                this.extensions.putAll(extensions)
            }
        }

        @JsonAnyGetter
        Map<String, Object> getExtensions() {
            return extensions
        }
    }

    @Introspected
    static class Token {
        @NonNull
        private final Map<String, Object> extensions = new HashMap<>()

        Token(@Nullable Map<String, Object> extensions) {
            if (extensions != null) {
                this.extensions.putAll(extensions)
            }
        }

        @JsonAnyGetter
        Map<String, Object> getExtensions() {
            return extensions
        }
    }

    @Serdeable
    static class TokenSerdeable {
        @NonNull
        private final Map<String, Object> extensions = new HashMap<>()

        TokenSerdeable(@Nullable Map<String, Object> extensions) {
            if (extensions != null) {
                this.extensions.putAll(extensions)
            }
        }

        @JsonAnyGetter
        Map<String, Object> getExtensions() {
            return extensions
        }
    }
}

