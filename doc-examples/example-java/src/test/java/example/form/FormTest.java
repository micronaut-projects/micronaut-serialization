package example.form;

import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Consumes;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Status;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@Property(name = "spec.name", value = "FormTest")
@MicronautTest
class FormTest {

    @Test
    void formWithPojoWorks(@Client("/")HttpClient httpClient) {
        BlockingHttpClient client = httpClient.toBlocking();
        assertDoesNotThrow(() -> client.exchange(formRequest("pojo")));
    }

    @Test
    void formWithMethodParamsWorks(@Client("/")HttpClient httpClient) {
        BlockingHttpClient client = httpClient.toBlocking();
        assertDoesNotThrow(() -> client.exchange(formRequest("methodparameter")));
    }

    private HttpRequest<?> formRequest(String path) {
        Map<String, String> form = Map.of("grant_type", "client_credentials");
        return HttpRequest.POST(UriBuilder.of("/token").path(path).build(), form)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED);
    }

    @Requires(property = "spec.name", value = "FormTest")
    @Controller("/token")
    static class TokenController {

        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        @Post("/pojo")
        @Status(HttpStatus.OK)
        void tokenPojo(@Body ClientCredentialsForm form) {
        }

        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        @Post("/methodparameter")
        @Status(HttpStatus.OK)
        void tokenMethodParam(@NonNull String grant_type,
                              @Nullable String client_id,
                              @Nullable String client_secret) {
        }
    }

    @Serdeable
    static class ClientCredentialsForm {

        @NonNull
        private final String grant_type;

        @Nullable
        private final String client_id;

        @Nullable
        private final String client_secret;

        public ClientCredentialsForm(@NonNull String grant_type, @Nullable String client_id, @Nullable String client_secret) {
            this.grant_type = grant_type;
            this.client_id = client_id;
            this.client_secret = client_secret;
        }

        @Nullable
        public String getGrant_type() {
            return grant_type;
        }

        @Nullable
        public String getClient_id() {
            return client_id;
        }

        @Nullable
        public String getClient_secret() {
            return client_secret;
        }
    }

}
