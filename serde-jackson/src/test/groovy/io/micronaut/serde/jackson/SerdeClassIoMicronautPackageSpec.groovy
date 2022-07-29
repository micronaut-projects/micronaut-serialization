package io.micronaut.serde.jackson

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.PendingFeature
import spock.lang.Specification
import io.micronaut.security.token.jwt.render.BearerAccessRefreshToken
import io.micronaut.serde.ObjectMapper

@MicronautTest(startApplication = false)
class SerdeClassIoMicronautPackageSpec extends Specification {
    @Inject
    ObjectMapper objectMapper

    @PendingFeature
    void "a class in io.micronaut package should be Serializable and Deserializable"() {
        given:

        String json = "{\"username\":\"admin@local.com\",\"access_token\":\"eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbkBsb2NhbC5jb20iLCJjb250ZW50LWxlbmd0aCI6IjEwNSIsInByb2R1Y3QiOiJwcm9kdWN0IiwibmJmIjoxNjU5MDc4ODcwLCJyb2xlcyI6W10sImlzcyI6InRlc3RhcHBsaWNhdGlvbiIsImhvc3QiOiJsb2NhbGhvc3Q6NTQ3MjUiLCJjb25uZWN0aW9uIjoiY2xvc2UiLCJjb250ZW50LXR5cGUiOiJhcHBsaWNhdGlvblwvanNvbiIsImV4cCI6MTY1OTA4MjQ3MCwiaWF0IjoxNjU5MDc4ODcwfQ.ugdU-pYUgwU44Skd2jmP4x_aNLAVhrIuSYwyW21ngAg\",\"token_type\":\"Bearer\",\"expires_in\":3600}";
        when:
        BearerAccessRefreshToken bearerAccessRefreshToken = new  BearerAccessRefreshToken("admin@local.com",
                Collections.emptyList(),
                3600,
                "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJhZG1pbkBsb2NhbC5jb20iLCJjb250ZW50LWxlbmd0aCI6IjEwNSIsInByb2R1Y3QiOiJwcm9kdWN0IiwibmJmIjoxNjU5MDc4ODcwLCJyb2xlcyI6W10sImlzcyI6InRlc3RhcHBsaWNhdGlvbiIsImhvc3QiOiJsb2NhbGhvc3Q6NTQ3MjUiLCJjb25uZWN0aW9uIjoiY2xvc2UiLCJjb250ZW50LXR5cGUiOiJhcHBsaWNhdGlvblwvanNvbiIsImV4cCI6MTY1OTA4MjQ3MCwiaWF0IjoxNjU5MDc4ODcwfQ.ugdU-pYUgwU44Skd2jmP4x_aNLAVhrIuSYwyW21ngAg",
                null,
                "Bearer");
        String result = objectMapper.writeValueAsString(bearerAccessRefreshToken);

        then:
        json == result

        when:
        bearerAccessRefreshToken = objectMapper.readValue(json, BearerAccessRefreshToken.class);

        then:
        bearerAccessRefreshToken
        bearerAccessRefreshToken.accessToken
    }

}
