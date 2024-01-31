package io.micronaut.serde.jackson.annotation.http;

import io.micronaut.http.client.annotation.Client;

@Client("/")
public interface ApiClient extends Api {
}
